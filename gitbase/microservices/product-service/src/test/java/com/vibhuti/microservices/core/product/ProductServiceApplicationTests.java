package com.vibhuti.microservices.core.product;

import static reactor.core.publisher.Mono.just;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.vibhuti.microservices.api.core.product.Product;
import com.vibhuti.microservices.core.product.persistence.ProductRepository;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ProductServiceApplicationTests extends MongoDbTestBase{

	@Autowired
	private WebTestClient client;
	@Autowired
	private ProductRepository repository;

	@BeforeEach
	public void setupDb() {
		this.repository.deleteAll();
	}

	@Test
	void getProductById() {
		int productId = 1;
		postAndVerifyProduct(productId, HttpStatus.OK);
		assertTrue(this.repository.findByProductId(productId).isPresent());
		getAndVerifyProduct(productId, HttpStatus.OK).jsonPath("$.productId").isEqualTo(productId);
	}

	   @Test
	void getProductInvalidParameterString() {
		getAndVerifyProduct("/no-integer", BAD_REQUEST).jsonPath("$.path").isEqualTo("/product/no-integer");
		// .jsonPath("$.message").isEqualTo("Type mismatch.");
	}
	
	 @Test
	  void duplicateError() {
	    int productId = 1;
	    postAndVerifyProduct(productId, HttpStatus.OK);
	    assertTrue(repository.findByProductId(productId).isPresent());
	    postAndVerifyProduct(productId, UNPROCESSABLE_ENTITY)
	      .jsonPath("$.path").isEqualTo("/product")
	      .jsonPath("$.message").isEqualTo("Duplicate key, Product Id: " + productId);
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
		postAndVerifyProduct(productId, HttpStatus.OK);
		assertTrue(repository.findByProductId(productId).isPresent());
		deleteAndVerifyProduct(productId, HttpStatus.OK);
		assertFalse(this.repository.findByProductId(productId).isPresent());
		deleteAndVerifyProduct(productId, HttpStatus.OK);
	}

	@Test
	void getProductInvalidParameterNegativeValue() {
		int productIdInvalid = -1;
		getAndVerifyProduct(productIdInvalid, UNPROCESSABLE_ENTITY)
	      .jsonPath("$.path").isEqualTo("/product/" + productIdInvalid)
	      .jsonPath("$.message").isEqualTo("Invalid productId: " + productIdInvalid);

	}

	private WebTestClient.BodyContentSpec postAndVerifyProduct(int productId, HttpStatus expectedStatus) {
		Product product = new Product(productId, "Name " + productId, productId, "SA");
		return client.post().uri("/product")
				.body(just(product), Product.class)
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().isEqualTo(expectedStatus).
				expectHeader().contentType(APPLICATION_JSON)
				.expectBody();
	}

	private WebTestClient.BodyContentSpec getAndVerifyProduct(int productId, HttpStatus expectedStatus) {
		return getAndVerifyProduct("/" + productId, expectedStatus);
	}

	private WebTestClient.BodyContentSpec getAndVerifyProduct(String productIdPath, HttpStatus expectedStatus) {
		return client.get().uri("/product" + productIdPath).accept(APPLICATION_JSON).exchange().expectStatus()
				.isEqualTo(expectedStatus).expectHeader().contentType(APPLICATION_JSON).expectBody();
	}

	private WebTestClient.BodyContentSpec deleteAndVerifyProduct(int productId, HttpStatus expectedStatus) {
		return client.delete().uri("/product/" + productId).accept(APPLICATION_JSON).exchange().expectStatus()
				.isEqualTo(expectedStatus).expectBody();
	}

}
