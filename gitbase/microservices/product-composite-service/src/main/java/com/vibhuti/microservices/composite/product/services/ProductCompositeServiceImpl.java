package com.vibhuti.microservices.composite.product.services;

import static java.util.logging.Level.FINE;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import com.vibhuti.microservices.api.composite.product.ProductAggregate;
import com.vibhuti.microservices.api.composite.product.ProductCompositeService;
import com.vibhuti.microservices.api.composite.product.RecommendationSummary;
import com.vibhuti.microservices.api.composite.product.ReviewSummary;
import com.vibhuti.microservices.api.composite.product.ServiceAddresses;
import com.vibhuti.microservices.api.core.product.Product;
import com.vibhuti.microservices.api.core.recommendation.Recommendation;
import com.vibhuti.microservices.api.core.review.Review;
import com.vibhuti.microservices.util.ServiceUtil;

import reactor.core.publisher.Mono;

@RestController
public class ProductCompositeServiceImpl implements ProductCompositeService {

	private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeServiceImpl.class);
	private final ProductCompositeIntegration compositeIntegration;
	private final ServiceUtil serviceUtil;

	@Autowired
	public ProductCompositeServiceImpl(ServiceUtil serviceUtil, ProductCompositeIntegration compositeIntegration) {
		this.serviceUtil = serviceUtil;
		this.compositeIntegration = compositeIntegration;
	}

	@Override
	public Mono<ProductAggregate> getProduct(int productId) {
		 LOG.info("Will get composite product info for product.id={}", productId);
		
		 return Mono.zip(
				 values -> createProductAggregate((Product)values[0], (List<Recommendation>)values[1],(List<Review>) values[2], serviceUtil.getServiceAddress()),
				 this.compositeIntegration.getProduct(productId),
				 this.compositeIntegration.getRecommendations(productId).collectList(),
				 this.compositeIntegration.getReviews(productId).collectList())
		 .doOnError(ex -> LOG.warn("getCompositeProduct failed: {}", ex.toString()))
	      .log(LOG.getName(), FINE);

		    
	}

	private ProductAggregate createProductAggregate(Product product, List<Recommendation> recommendations,
			List<Review> reviews, String serviceAddress) {

		// 1. Setup product info
		int productId = product.getProductId();
		String name = product.getName();
		int weight = product.getWeight();

		// 2. Copy summary recommendation info, if available
		List<RecommendationSummary> recommendationSummaries = (recommendations == null) ? null
				: recommendations.stream()
						.map(r -> new RecommendationSummary(r.getRecommendationId(), r.getAuthor(), r.getRate(),r.getContent()))
						.collect(Collectors.toList());

		// 3. Copy summary review info, if available
		List<ReviewSummary> reviewSummaries = (reviews == null) ? null
				: reviews.stream().map(r -> new ReviewSummary(r.getReviewId(), r.getAuthor(), r.getSubject(),r.getContent()))
						.collect(Collectors.toList());

		// 4. Create info regarding the involved microservices addresses
		String productAddress = product.getServiceAddress();
		String reviewAddress = (reviews != null && reviews.size() > 0) ? reviews.get(0).getServiceAddress() : "";
		String recommendationAddress = (recommendations != null && recommendations.size() > 0)
				? recommendations.get(0).getServiceAddress()
				: "";
		ServiceAddresses serviceAddresses = new ServiceAddresses(serviceAddress, productAddress, reviewAddress,
				recommendationAddress);

		return new ProductAggregate(productId, name, weight, recommendationSummaries, reviewSummaries,
				serviceAddresses);
	}

	@Override
	public Mono<Void> createProduct(ProductAggregate body) {
		try {
			
		      List<Mono> monoList = new ArrayList<>();

			LOG.debug("createCompositeProduct: creates a new composite entity for productId: {}", body.getProductId());

			Product product = new Product(body.getProductId(), body.getName(), body.getWeight(), null);
			monoList.add(this.compositeIntegration.createProduct(product));

			if (body.getRecommendations() != null) {
				body.getRecommendations().forEach(r -> {
					Recommendation recommendation = new Recommendation(body.getProductId(), r.getRecommendationId(),
							r.getAuthor(), r.getRate(), r.getContent(), null);
					monoList.add(compositeIntegration.createRecommendation(recommendation));
				});
			}

			if (body.getReviews() != null) {
				body.getReviews().forEach(r -> {
					Review review = new Review(body.getProductId(), r.getReviewId(), r.getAuthor(), r.getSubject(),
							r.getContent(), null);
					monoList.add(compositeIntegration.createReview(review));
				});
			}
			
			

			LOG.debug("createCompositeProduct: composite entities created for productId: {}", body.getProductId());
			  return Mono.zip(r -> "", monoList.toArray(new Mono[0]))
				        .doOnError(ex -> LOG.warn("createCompositeProduct failed: {}", ex.toString()))
				        .then();

		} catch (RuntimeException re) {
			LOG.warn("createCompositeProduct failed", re);
			throw re;
		}
	}

	@Override
	public Mono<Void> deleteProduct(int productId) {
		try
		{
		LOG.debug("deleteCompositeProduct: Deletes a product aggregate for productId: {}", productId);
	    /*compositeIntegration.deleteProduct(productId);
	    compositeIntegration.deleteRecommendations(productId);
	    compositeIntegration.deleteReviews(productId);*/
	      return Mono.zip(
	    	        r -> "",
	    	        compositeIntegration.deleteProduct(productId),
	    	        compositeIntegration.deleteRecommendations(productId),
	    	        compositeIntegration.deleteReviews(productId))
	    	        .doOnError(ex -> LOG.warn("delete failed: {}", ex.toString()))
	    	        .log(LOG.getName(), FINE).then();
		} catch (RuntimeException re) {
		      LOG.warn("deleteCompositeProduct failed: {}", re.toString());
		      throw re;
		    } 
	}

}
