package com.vibhuti.microservices.composite.product.services;

import static com.vibhuti.microservices.api.event.Event.Type.CREATE;
import static com.vibhuti.microservices.api.event.Event.Type.DELETE;
import static java.util.logging.Level.FINE;
import static reactor.core.publisher.Flux.empty;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibhuti.microservices.api.core.product.Product;
import com.vibhuti.microservices.api.core.product.ProductService;
import com.vibhuti.microservices.api.core.recommendation.Recommendation;
import com.vibhuti.microservices.api.core.recommendation.RecommendationService;
import com.vibhuti.microservices.api.core.review.Review;
import com.vibhuti.microservices.api.core.review.ReviewService;
import com.vibhuti.microservices.api.event.Event;
import com.vibhuti.microservices.exception.InvalidInputException;
import com.vibhuti.microservices.exception.NotFoundException;
import com.vibhuti.microservices.util.HttpErrorInfo;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {

	private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);

	private final ObjectMapper mapper;

	private final String productServiceUrl;
	private final String recommendationServiceUrl;
	private final String reviewServiceUrl;
	private final StreamBridge streamBridge;

	private final WebClient webClient;
	private final Scheduler publishEventScheduler;

	@Autowired
	public ProductCompositeIntegration(@Qualifier("publishEventScheduler") Scheduler publishEventScheduler,
			WebClient.Builder webClient, ObjectMapper mapper, StreamBridge streamBridge,
			@Value("${app.product-service.host}") String productServiceHost,
			@Value("${app.product-service.port}") int productServicePort,
			@Value("${app.recommendation-service.host}") String recommendationServiceHost,
			@Value("${app.recommendation-service.port}") int recommendationServicePort,
			@Value("${app.review-service.host}") String reviewServiceHost,
			@Value("${app.review-service.port}") int reviewServicePort) {
		this.publishEventScheduler = publishEventScheduler;
		this.webClient = webClient.build();
		this.mapper = mapper;
		this.streamBridge = streamBridge;
		productServiceUrl = "http://" + productServiceHost + ":" + productServicePort ;
		recommendationServiceUrl = "http://" + recommendationServiceHost + ":" + recommendationServicePort;
		reviewServiceUrl = "http://" + reviewServiceHost + ":" + reviewServicePort  ;

	}

	@Override
	public Mono<Product> getProduct(int productId) {

		String url = this.productServiceUrl + "/product/" + productId;
		return webClient.get().uri(url).retrieve().bodyToMono(Product.class).log(LOG.getName(), FINE)
				.onErrorMap(WebClientResponseException.class, ex -> handleException(ex));

	}//

	private Throwable handleException(Throwable ex) {

		if (!(ex instanceof WebClientResponseException)) {
			LOG.warn("Got a unexpected error: {}, will rethrow it", ex.toString());
			return ex;
		}

		WebClientResponseException wcre = (WebClientResponseException) ex;

		switch (HttpStatus.resolve(wcre.getStatusCode().value())) {

		case NOT_FOUND:
			return new NotFoundException(getErrorMessage(wcre));

		case UNPROCESSABLE_ENTITY:
			return new InvalidInputException(getErrorMessage(wcre));

		default:
			LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", wcre.getStatusCode());
			LOG.warn("Error body: {}", wcre.getResponseBodyAsString());
			return ex;
		}
	}

	private String getErrorMessage(WebClientResponseException ex) {
		try {
			return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
		} catch (IOException ioex) {
			return ex.getMessage();
		}
	}

	private String getErrorMessage(HttpClientErrorException ex) {
		try {
			return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
		} catch (IOException ioex) {
			return ex.getMessage();
		}
	}

	public Flux<Recommendation> getRecommendations(int productId) {
		String url = recommendationServiceUrl + "/recommendation?productId=" + productId;
		LOG.debug("Will call getRecommendations API on URL: {}", url);

		return webClient.get().uri(url).retrieve().bodyToFlux(Recommendation.class).onErrorResume(e -> empty());
	}

	public Flux<Review> getReviews(int productId) {
		String url = reviewServiceUrl + "/review?productId=" + productId;
		return webClient.get().uri(url).retrieve().bodyToFlux(Review.class).onErrorResume(e -> empty());
	}

	@Override
	public Mono<Review> createReview(Review body) {
		return Mono.fromCallable(() -> {
		      sendMessage("reviews-out-0", new Event(CREATE, body.getProductId(), body));
		      return body;
		    }).subscribeOn(publishEventScheduler);
	}

	@Override
	public Mono<Void> deleteReviews(int productId) {
	    return Mono.fromRunnable(() -> sendMessage("reviews-out-0", new Event(DELETE, productId, null)))
	    	      .subscribeOn(publishEventScheduler).then();
	}

	@Override
	public Mono<Recommendation> createRecommendation(Recommendation body) {
		return Mono.fromCallable(() -> {
		      sendMessage("recommendations-out-0", new Event(CREATE, body.getProductId(), body));
		      return body;
		    }).subscribeOn(publishEventScheduler);
	}

	@Override
	public Mono<Void> deleteRecommendations(int productId) {
		   return Mono.fromRunnable(() -> sendMessage("recommendations-out-0", new Event(DELETE, productId, null)))
				      .subscribeOn(publishEventScheduler).then();
	}

	@Override
	public Mono<Product> createProduct(Product body) {
		  return Mono.fromCallable(() -> {
		      sendMessage("products-out-0", new Event(CREATE, body.getProductId(), body));
		      return body;
		    }).subscribeOn(publishEventScheduler);

	}

	@Override
	public Mono<Void> deleteProduct(int productId) {
		return Mono.fromRunnable(() -> sendMessage("products-out-0", new Event(DELETE, productId, null)))
			      .subscribeOn(publishEventScheduler).then();
	}

	private RuntimeException handleHttpClientException(HttpClientErrorException ex) {
		switch (HttpStatus.resolve(ex.getStatusCode().value())) {

		case NOT_FOUND:
			return new NotFoundException(getErrorMessage(ex));

		case UNPROCESSABLE_ENTITY:
			return new InvalidInputException(getErrorMessage(ex));

		default:
			LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", ex.getStatusCode());
			LOG.warn("Error body: {}", ex.getResponseBodyAsString());
			return ex;
		}
	}
	
	 private void sendMessage(String bindingName, Event event) {
		    LOG.debug("Sending a {} message to {}", event.getEventType(), bindingName);
		    Message message = MessageBuilder.withPayload(event)
		      .setHeader("partitionKey", event.getKey())
		      .build();
		    streamBridge.send(bindingName, message);
		  }
	 
	 public Mono<Health> getProductHealth() {
		    return getHealth(productServiceUrl);
		  }

		  public Mono<Health> getRecommendationHealth() {
		    return getHealth(recommendationServiceUrl);
		  }

		  public Mono<Health> getReviewHealth() {
		    return getHealth(reviewServiceUrl);
		  }

		  private Mono<Health> getHealth(String url) {
		    url += "/actuator/health";
		    LOG.debug("Will call the Health API on URL: {}", url);
		    return webClient.get().uri(url).retrieve().bodyToMono(String.class)
		      .map(s -> new Health.Builder().up().build())
		      .onErrorResume(ex -> Mono.just(new Health.Builder().down(ex).build()))
		      .log(LOG.getName(), FINE);
		  }
}
