package com.vibhuti.microservices.core.product;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static reactor.core.publisher.Mono.just;
import static com.vibhuti.microservices.api.event.Event.Type.CREATE;
import static com.vibhuti.microservices.api.event.Event.Type.DELETE;
import java.util.function.Consumer;
import static org.springframework.http.HttpStatus.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.vibhuti.microservices.api.core.product.Product;
import com.vibhuti.microservices.api.event.Event;
import com.vibhuti.microservices.core.product.persistence.ProductRepository;
import com.vibhuti.microservices.exception.InvalidInputException;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ProductServiceApplicationTests extends MongoDbTestBase{

	@Autowired
	private WebTestClient client;
	@Autowired
	private ProductRepository repository;
	
	@Autowired
	@Qualifier("messageProcessor")
	private Consumer<Event<Integer, Product>> messageProcessor;

	@BeforeEach
	public void setupDb() {
		this.repository.deleteAll().block();
	}

	@Test
	void getProductById() {
		int productId = 1;
		assertNull(repository.findByProductId(productId).block());
	    assertEquals(0, (long)repository.count().block());
	    sendCreateProductEvent(productId);
	    assertNotNull(repository.findByProductId(productId).block());
	    assertEquals(1, (long)repository.count().block());

	    getAndVerifyProduct(productId, OK)
	      .jsonPath("$.productId").isEqualTo(productId);

	}

	   @Test
	void getProductInvalidParameterString() {
		getAndVerifyProduct("/no-integer", BAD_REQUEST).jsonPath("$.path").isEqualTo("/product/no-integer");
		// .jsonPath("$.message").isEqualTo("Type mismatch.");
	}
	
	 @Test
	  void duplicateError() {
	    
	    int productId = 1;

	    assertNull(repository.findByProductId(productId).block());

	    sendCreateProductEvent(productId);

	    assertNotNull(repository.findByProductId(productId).block());

	    InvalidInputException thrown = assertThrows(
	      InvalidInputException.class,
	      () -> sendCreateProductEvent(productId),
	      "Expected a InvalidInputException here!");
	    assertEquals("Duplicate key, Product Id: " + productId, thrown.getMessage());   
	    
	    
	  }

	@Test
	void getProductNotFound() {
		int productIdNotFound = 13;
		getAndVerifyProduct(productIdNotFound, HttpStatus.NOT_FOUND).jsonPath("$.path")
				.isEqualTo("/product/" + productIdNotFound);
		// .jsonPath("$.message").isEqualTo("No product found for productId: " +
		// productIdNotFound);
	}

	@Test
	void deleteProduct() {
		int productId = 1;
		  sendCreateProductEvent(productId);
		    assertNotNull(repository.findByProductId(productId).block());

		    sendDeleteProductEvent(productId);
		    assertNull(repository.findByProductId(productId).block());

		    sendDeleteProductEvent(productId);
	}

	

	  @Test
	  void getProductInvalidParameterNegativeValue() {

	    int productIdInvalid = -1;

	    getAndVerifyProduct(productIdInvalid, UNPROCESSABLE_ENTITY)
	      .jsonPath("$.path").isEqualTo("/product/" + productIdInvalid)
	      .jsonPath("$.message").isEqualTo("Invalid productId: " + productIdInvalid);
	  }

	 private WebTestClient.BodyContentSpec getAndVerifyProduct(int productId, HttpStatus expectedStatus) {
		    return getAndVerifyProduct("/" + productId, expectedStatus);
		  }

		  private WebTestClient.BodyContentSpec getAndVerifyProduct(String productIdPath, HttpStatus expectedStatus) {
		    return client.get()
		      .uri("/product" + productIdPath)
		      .accept(APPLICATION_JSON)
		      .exchange()
		      .expectStatus().isEqualTo(expectedStatus)
		      .expectHeader().contentType(APPLICATION_JSON)
		      .expectBody();
		  }

		  private void sendCreateProductEvent(int productId) {
		    Product product = new Product(productId, "Name " + productId, productId, "SA");
		    Event<Integer, Product> event = new Event(CREATE, productId, product);
		    messageProcessor.accept(event);
		  }

		  private void sendDeleteProductEvent(int productId) {
		    Event<Integer, Product> event = new Event(DELETE, productId, null);
		    messageProcessor.accept(event);
		  }

}
