package com.vibhuti.microservices.composite.product.services;

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
import com.vibhuti.microservices.exception.NotFoundException;
import com.vibhuti.microservices.util.ServiceUtil;

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
	public ProductAggregate getProduct(int productId) {
		Product product = compositeIntegration.getProduct(productId);
		if (product == null) {
			throw new NotFoundException("No product found for productId: " + productId);
		}

		List<Recommendation> recommendations = compositeIntegration.getRecommendations(productId);

		List<Review> reviews = compositeIntegration.getReviews(productId);

		return createProductAggregate(product, recommendations, reviews, serviceUtil.getServiceAddress());
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
	public void createProduct(ProductAggregate body) {
		try {

			LOG.debug("createCompositeProduct: creates a new composite entity for productId: {}", body.getProductId());

			Product product = new Product(body.getProductId(), body.getName(), body.getWeight(), null);
			compositeIntegration.createProduct(product);

			if (body.getRecommendations() != null) {
				body.getRecommendations().forEach(r -> {
					Recommendation recommendation = new Recommendation(body.getProductId(), r.getRecommendationId(),
							r.getAuthor(), r.getRate(), r.getContent(), null);
					compositeIntegration.createRecommendation(recommendation);
				});
			}

			if (body.getReviews() != null) {
				body.getReviews().forEach(r -> {
					Review review = new Review(body.getProductId(), r.getReviewId(), r.getAuthor(), r.getSubject(),
							r.getContent(), null);
					compositeIntegration.createReview(review);
				});
			}

			LOG.debug("createCompositeProduct: composite entities created for productId: {}", body.getProductId());

		} catch (RuntimeException re) {
			LOG.warn("createCompositeProduct failed", re);
			throw re;
		}
	}

	@Override
	public void deleteProduct(int productId) {
		LOG.debug("deleteCompositeProduct: Deletes a product aggregate for productId: {}", productId);
	    compositeIntegration.deleteProduct(productId);
	    compositeIntegration.deleteRecommendations(productId);
	    compositeIntegration.deleteReviews(productId);
	    LOG.debug("deleteCompositeProduct: aggregate entities deleted for productId: {}", productId);
	}

}
