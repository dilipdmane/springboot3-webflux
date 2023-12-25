package com.vibhuti.microservices.core.review.recommendation;

import static com.vibhuti.microservices.api.event.Event.Type.CREATE;
import static com.vibhuti.microservices.api.event.Event.Type.DELETE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static reactor.core.publisher.Mono.just;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.vibhuti.microservices.api.core.recommendation.Recommendation;
import com.vibhuti.microservices.api.event.Event;
import com.vibhuti.microservices.core.review.recommendation.persistence.RecommendationRepository;
import com.vibhuti.microservices.exception.InvalidInputException;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RecommendationServiceApplicationTests extends MongoDbTestBase {

	@Autowired
	private WebTestClient client;
	@Autowired
	private RecommendationRepository repository;
	
	@Autowired
	@Qualifier("messageProcessor")
	private Consumer<Event<Integer, Recommendation>> messageProcessor;

	@BeforeEach
	void setupDb() {
		this.repository.deleteAll().block();
	}

	@Test
	void getRecommendationsByProductId() {
		int productId = 1;
		 sendCreateRecommendationEvent(productId, 1);
		 sendCreateRecommendationEvent(productId, 2);
		 sendCreateRecommendationEvent(productId, 3);
		 assertEquals(3, (long)this.repository.findByProductId(productId).count().block());
		 getAndVerifyRecommendationsByProductId(productId, HttpStatus.OK)
	      .jsonPath("$.length()").isEqualTo(3)
	      .jsonPath("$[2].productId").isEqualTo(productId)
	      .jsonPath("$[2].recommendationId").isEqualTo(3);
	}

	@Test
	void duplicateError() {
		int productId = 1;
		int recommendationId = 1;
		sendCreateRecommendationEvent(productId, recommendationId);
	    assertEquals(1, (long)repository.count().block());
	    InvalidInputException thrown = assertThrows(
	    	      InvalidInputException.class,
	    	      () -> sendCreateRecommendationEvent(productId, recommendationId),
	    	      "Expected a InvalidInputException here!");
	    assertEquals("Duplicate key, Product Id: 1, Recommendation Id:1", thrown.getMessage());
	    assertEquals(1, (long)repository.count().block());
	}

	@Test
	void deleteRecommendations() {
		int productId = 1;
		int recommendationId = 1;
	    sendCreateRecommendationEvent(productId, recommendationId);
	    assertEquals(1, (long)repository.findByProductId(productId).count().block());
	    sendDeleteRecommendationEvent(productId);
	    assertEquals(0, (long)repository.findByProductId(productId).count().block());
	    sendDeleteRecommendationEvent(productId);
	}

	@Test
	void getRecommendationsMissingParameter() {

		getAndVerifyRecommendationsByProductId("", HttpStatus.BAD_REQUEST).jsonPath("$.path")
				.isEqualTo("/recommendation")
				.jsonPath("$.message");//				.isEqualTo("Required query parameter 'productId' is not present.");
	}

	@Test
	void getRecommendationsInvalidParameter() {

		getAndVerifyRecommendationsByProductId("?productId=no-integer", HttpStatus.BAD_REQUEST).jsonPath("$.path")
				.isEqualTo("/recommendation");//	.jsonPath("$.message").isEqualTo("Type mismatch.");
	}

	@Test
	void getRecommendationsNotFound() {

		getAndVerifyRecommendationsByProductId("?productId=113", HttpStatus.OK).jsonPath("$.length()").isEqualTo(0);
	}

	@Test
	void getRecommendationsInvalidParameterNegativeValue() {

		int productIdInvalid = -1;

		getAndVerifyRecommendationsByProductId("?productId=" + productIdInvalid, HttpStatus.UNPROCESSABLE_ENTITY)
				.jsonPath("$.path").isEqualTo("/recommendation").jsonPath("$.message")
				.isEqualTo("Invalid productId: " + productIdInvalid);
	}

	
	
	private void sendCreateRecommendationEvent(int productId, int recommendationId) {
	    Recommendation recommendation = new Recommendation(productId, recommendationId, "Author " + recommendationId, recommendationId, "Content " + recommendationId, "SA");
	    Event<Integer, Recommendation> event = new Event(CREATE, productId, recommendation);
	    messageProcessor.accept(event);
	  }

	  private void sendDeleteRecommendationEvent(int productId) {
	    Event<Integer, Recommendation> event = new Event(DELETE, productId, null);
	    messageProcessor.accept(event);
	  }
	  
	  
	  private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(int productId, HttpStatus expectedStatus) {
		    return getAndVerifyRecommendationsByProductId("?productId=" + productId, expectedStatus);
		  }

		  private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(String productIdQuery, HttpStatus expectedStatus) {
		    return client.get()
		      .uri("/recommendation" + productIdQuery)
		      .accept(APPLICATION_JSON)
		      .exchange()
		      .expectStatus().isEqualTo(expectedStatus)
		      .expectHeader().contentType(APPLICATION_JSON)
		      .expectBody();
		  }

}
