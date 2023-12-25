package com.vibhuti.microservices.core.review.recommendation.services;
import static java.util.logging.Level.FINE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestController;

import com.vibhuti.microservices.api.core.recommendation.Recommendation;
import com.vibhuti.microservices.api.core.recommendation.RecommendationService;
import com.vibhuti.microservices.core.review.recommendation.persistence.RecommendationEntity;
import com.vibhuti.microservices.core.review.recommendation.persistence.RecommendationRepository;
import com.vibhuti.microservices.exception.InvalidInputException;
import com.vibhuti.microservices.util.ServiceUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class RecommendationServiceImpl implements RecommendationService {

	private static final Logger LOG = LoggerFactory.getLogger(RecommendationServiceImpl.class);

	private final ServiceUtil serviceUtil;
	private final RecommendationMapper recommendationMapper;
	private final RecommendationRepository recommendationRepository;

	@Autowired
	public RecommendationServiceImpl(ServiceUtil serviceUtil, RecommendationMapper recommendationMapper,
			RecommendationRepository recommendationRepository) {
		this.serviceUtil = serviceUtil;
		this.recommendationMapper = recommendationMapper;
		this.recommendationRepository = recommendationRepository;
	}

	@Override
	public Flux<Recommendation> getRecommendations(int productId) {

		if (productId < 1) {
			throw new InvalidInputException("Invalid productId: " + productId);
		}
		 LOG.info("Will get recommendations for product with id={}", productId);

		    return recommendationRepository.findByProductId(productId)
		      .log(LOG.getName(), FINE)
		      .map(e -> recommendationMapper.entityToApi(e))
		      .map(e -> setServiceAddress(e));
	}

	@Override
	public Mono<Recommendation> createRecommendation(Recommendation body) {
		if( body.getProductId()<1)
		{
			throw new InvalidInputException("Invalid productId: " + body.getProductId());
		}
		
		RecommendationEntity recommendationEntity = this.recommendationMapper.apiToEntity(body);
		Mono<Recommendation> recommendation = this.recommendationRepository.save(recommendationEntity)
		.log(LOG.getName(),FINE)
		.onErrorMap(DuplicateKeyException.class, ex -> (new InvalidInputException("Duplicate key, Product Id: " + body.getProductId()
					+ ", Recommendation Id:" + body.getRecommendationId())
				))
		.map(e -> this.recommendationMapper.entityToApi(e));
		return recommendation;
	}

	@Override
	public Mono<Void> deleteRecommendations(int productId) {
		if( productId <1)
		{
			throw new InvalidInputException("Invalid productId: " + productId);
		}
		 LOG.debug("deleteRecommendations: tries to delete recommendations for the product with productId: {}", productId);
		 //this.recommendationRepository.deleteAll(this.recommendationRepository.findByProductId(productId));
		 return this.recommendationRepository.deleteAll(this.recommendationRepository.findByProductId(productId));		 
	}
	
	private Recommendation setServiceAddress(Recommendation e) {
	    e.setServiceAddress(serviceUtil.getServiceAddress());
	    return e;
	  }
}