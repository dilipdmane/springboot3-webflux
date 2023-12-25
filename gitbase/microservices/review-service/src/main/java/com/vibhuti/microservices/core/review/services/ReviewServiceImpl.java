package com.vibhuti.microservices.core.review.services;
import static java.util.logging.Level.FINE;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.RestController;

import com.vibhuti.microservices.api.core.review.Review;
import com.vibhuti.microservices.api.core.review.ReviewService;
import com.vibhuti.microservices.core.review.persistence.ReviewEntity;
import com.vibhuti.microservices.core.review.persistence.ReviewMapper;
import com.vibhuti.microservices.core.review.persistence.ReviewRepository;
import com.vibhuti.microservices.exception.InvalidInputException;
import com.vibhuti.microservices.util.ServiceUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@RestController
public class ReviewServiceImpl implements ReviewService {

	private static final Logger LOG = LoggerFactory.getLogger(ReviewServiceImpl.class);

	private final ServiceUtil serviceUtil;
	private final ReviewRepository reviewRepository;
	private final ReviewMapper reviewMapper;	
	private final Scheduler jdbcScheduler;

	@Autowired
	public ReviewServiceImpl(@Qualifier("jdbcScheduler") Scheduler jdbcScheduler,ServiceUtil serviceUtil, ReviewRepository repository, ReviewMapper mapper) {
		this.serviceUtil = serviceUtil;
		this.reviewMapper = mapper;
		this.reviewRepository = repository;
		this.jdbcScheduler = jdbcScheduler;
	}

	@Override
	public Flux<Review> getReviews(int productId) {

		if (productId < 1) {
			throw new InvalidInputException("Invalid productId: " + productId);
		}		
		return Mono.fromCallable(()-> internalGetReview(productId))
				.flatMapMany(Flux::fromIterable)
				.log(LOG.getName(), FINE);
		
	}
	
	private List<Review> internalGetReview(int productId)
	{
		List<ReviewEntity> reviewEntityList = this.reviewRepository.findByProductId(productId);
		List<Review> list = this.reviewMapper.entityListToApiList(reviewEntityList);
		list.stream().forEach(review -> review.setServiceAddress(this.serviceUtil.getServiceAddress()));
		LOG.debug("getReviews: size for product: {}/{}", productId, list.size());
		return list;
	}

	@Override
	public Mono<Review> createReview(Review body) {
		 if (body.getProductId() < 1) {
		      throw new InvalidInputException("Invalid productId: " + body.getProductId());
		    }
		    return Mono.fromCallable(() -> internalCreateReview(body))
		      .subscribeOn(jdbcScheduler);
	}

	private Review  internalCreateReview(Review body) {
		 try {
		      ReviewEntity entity = this.reviewMapper.apiToEntity(body);
		      ReviewEntity newEntity = this.reviewRepository.save(entity);

		      LOG.debug("createReview: created a review entity: {}/{}", body.getProductId(), body.getReviewId());
		      return reviewMapper.entityToApi(newEntity);

		    } catch (DataIntegrityViolationException dive) {
		      throw new InvalidInputException("Duplicate key, Product Id: " + body.getProductId() + ", Review Id:" + body.getReviewId());
		    }
	}

	@Override
	  public Mono<Void> deleteReviews(int productId) {

	    if (productId < 1) {
	      throw new InvalidInputException("Invalid productId: " + productId);
	    }

	    return Mono.fromRunnable(() -> internalDeleteReviews(productId)).subscribeOn(jdbcScheduler).then();
	  }

	  private void internalDeleteReviews(int productId) {
	    LOG.debug("deleteReviews: tries to delete reviews for the product with productId: {}", productId);
	    this.reviewRepository.deleteAll(reviewRepository.findByProductId(productId));
	  }
}