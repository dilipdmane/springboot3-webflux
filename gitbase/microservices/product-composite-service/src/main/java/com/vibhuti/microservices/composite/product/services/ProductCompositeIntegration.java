package com.vibhuti.microservices.composite.product.services;

import static org.springframework.http.HttpMethod.GET;
import static java.util.logging.Level.FINE;
import static reactor.core.publisher.Flux.empty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.TypeResolutionContext.Empty;
import com.vibhuti.microservices.api.core.product.Product;
import com.vibhuti.microservices.api.core.product.ProductService;
import com.vibhuti.microservices.api.core.recommendation.Recommendation;
import com.vibhuti.microservices.api.core.recommendation.RecommendationService;
import com.vibhuti.microservices.api.core.review.Review;
import com.vibhuti.microservices.api.core.review.ReviewService;
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
		productServiceUrl = "http://" + productServiceHost + ":" + productServicePort + "/product";
		recommendationServiceUrl = "http://" + recommendationServiceHost + ":" + recommendationServicePort
				+ "/recommendation";
		reviewServiceUrl = "http://" + reviewServiceHost + ":" + reviewServicePort + "/review";

	}

	@Override
	public Mono<Product> getProduct(int productId) {

		String url = this.productServiceUrl + "/" + productId;
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
		String url = recommendationServiceUrl + "?productId=" + productId;
		LOG.debug("Will call getRecommendations API on URL: {}", url);

		return webClient.get().uri(url).retrieve().bodyToFlux(Recommendation.class).onErrorResume(e -> empty());
	}

	public Flux<Review> getReviews(int productId) {
		String url = reviewServiceUrl + "?productId=" + productId;
		return webClient.get().uri(url).retrieve().bodyToFlux(Review.class).onErrorResume(e -> empty());
	}

	@Override
	public Mono<Review> createReview(Review body) {
		try {
			String url = this.reviewServiceUrl;
			LOG.debug("Will post a new review to URL{}", url);
			Review review = this.restTemplate.postForObject(url, body, Review.class);
			return review;
		} catch (HttpClientErrorException ex) {
			throw handleHttpClientException(ex);
		}
	}

	@Override
	public Mono<Void> deleteReviews(int productId) {
		try {
			String url = reviewServiceUrl + "?productId=" + productId;
			LOG.debug("Will delete  review to URL: {}", url);
			this.restTemplate.delete(url);
		} catch (HttpClientErrorException ex) {
			throw handleHttpClientException(ex);
		}
	}

	@Override
	public Mono<Recommendation> createRecommendation(Recommendation body) {
		try {
			String url = recommendationServiceUrl;
			LOG.debug("Will post a new recommendation to URL: {}", url);

			Recommendation recommendation = restTemplate.postForObject(url, body, Recommendation.class);
			LOG.debug("Created a recommendation with id: {}", recommendation.getProductId());

			return recommendation;

		} catch (HttpClientErrorException ex) {
			throw handleHttpClientException(ex);
		}
	}

	@Override
	public Mono<Void> deleteRecommendations(int productId) {
		try {
			String url = recommendationServiceUrl + "?productId=" + productId;
			LOG.debug("Will delete  recommendation to URL: {}", url);
			this.restTemplate.delete(url);
		} catch (HttpClientErrorException ex) {
			throw handleHttpClientException(ex);
		}
	}

	@Override
	public Mono<Product> createProduct(Product body) {
		try {
			String url = this.productServiceUrl;
			LOG.debug("Will post a new product to URL: {}", url);
			Product product = this.restTemplate.postForObject(url, body, Product.class);
			LOG.debug("Created a product with id: {}", product.getProductId());
			return product;
		} catch (HttpClientErrorException ex) {
			throw handleHttpClientException(ex);
		}

	}

	@Override
	public Mono<Void> deleteProduct(int productId) {
		try {
			String url = this.productServiceUrl + "/" + productId;
			LOG.debug("Will delete  product to URL: {}", url);
			this.restTemplate.delete(url);
		} catch (HttpClientErrorException ex) {
			throw handleHttpClientException(ex);
		}
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
}
