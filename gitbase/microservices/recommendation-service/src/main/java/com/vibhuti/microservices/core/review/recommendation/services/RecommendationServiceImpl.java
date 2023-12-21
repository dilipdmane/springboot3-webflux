package com.vibhuti.microservices.core.review.recommendation.services;

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
	public List<Recommendation> getRecommendations(int productId) {

		if (productId < 1) {
			throw new InvalidInputException("Invalid productId: " + productId);
		}
		List<RecommendationEntity> listOfEntities = this.recommendationRepository.findByProductId(productId);
		List<Recommendation> list = this.recommendationMapper.entityListToApiList(listOfEntities);
		list.stream().forEach(r -> r.setServiceAddress(this.serviceUtil.getServiceAddress()));
		LOG.debug("getRecommendation response size: {}", list.size());
		return list;
	}

	@Override
	public Recommendation createRecommendation(Recommendation recommendation) {
		try {
			RecommendationEntity recommendationEntity = this.recommendationMapper.apiToEntity(recommendation);
			RecommendationEntity savedRecommendationEntity = this.recommendationRepository.save(recommendationEntity);
			LOG.debug("createRecommendation: created a recommendation entity: {}/{}", recommendation.getProductId(),
					recommendation.getRecommendationId());
			return this.recommendationMapper.entityToApi(savedRecommendationEntity);
		} catch (DuplicateKeyException e) {
			throw new InvalidInputException("Duplicate key, Product Id: " + recommendation.getProductId()
					+ ", Recommendation Id:" + recommendation.getRecommendationId());
		}
	}

	@Override
	public void deleteRecommendations(int productId) {
		 LOG.debug("deleteRecommendations: tries to delete recommendations for the product with productId: {}", productId);
		this.recommendationRepository.deleteAll(this.recommendationRepository.findByProductId(productId));
	}
}