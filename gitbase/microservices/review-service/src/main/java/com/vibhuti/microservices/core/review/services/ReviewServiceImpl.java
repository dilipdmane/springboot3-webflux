package com.vibhuti.microservices.core.review.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.RestController;

import com.vibhuti.microservices.api.core.review.Review;
import com.vibhuti.microservices.api.core.review.ReviewService;
import com.vibhuti.microservices.core.review.persistence.ReviewEntity;
import com.vibhuti.microservices.core.review.persistence.ReviewMapper;
import com.vibhuti.microservices.core.review.persistence.ReviewRepository;
import com.vibhuti.microservices.exception.InvalidInputException;
import com.vibhuti.microservices.util.ServiceUtil;

@RestController
public class ReviewServiceImpl implements ReviewService {

	private static final Logger LOG = LoggerFactory.getLogger(ReviewServiceImpl.class);

	private final ServiceUtil serviceUtil;
	private final ReviewRepository reviewRepository;
	private final ReviewMapper reviewMapper;

	@Autowired
	public ReviewServiceImpl(ServiceUtil serviceUtil, ReviewRepository repository, ReviewMapper mapper) {
		this.serviceUtil = serviceUtil;
		this.reviewMapper = mapper;
		this.reviewRepository = repository;
	}

	@Override
	public List<Review> getReviews(int productId) {

		if (productId < 1) {
			throw new InvalidInputException("Invalid productId: " + productId);
		}
		List<ReviewEntity> reviewEntityList = this.reviewRepository.findByProductId(productId);
		List<Review> list = this.reviewMapper.entityListToApiList(reviewEntityList);
		list.stream().forEach(review -> review.setServiceAddress(this.serviceUtil.getServiceAddress()));
		LOG.debug("getReviews: size for product: {}/{}", productId, list.size());
		return list;
	}

	@Override
	public Review createReview(Review body) {
		try {
			ReviewEntity reviewEntity = this.reviewMapper.apiToEntity(body);
			ReviewEntity savedReview = this.reviewRepository.save(reviewEntity);
			LOG.debug("createReview: created a review entity: {}/{}", body.getProductId(), body.getReviewId());
			return this.reviewMapper.entityToApi(savedReview);
		} catch (DataIntegrityViolationException e) {
			throw new InvalidInputException(
					"Duplicate key, Product Id: " + body.getProductId() + ", Review Id:" + body.getReviewId());
		}
	}

	@Override
	public void deleteReviews(int productId) {
		this.reviewRepository.deleteAll(this.reviewRepository.findByProductId(productId));
	}
}