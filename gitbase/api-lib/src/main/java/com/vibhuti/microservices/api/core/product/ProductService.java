package com.vibhuti.microservices.api.core.product;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import reactor.core.publisher.Mono;

public interface ProductService {

	/**
	 * Sample usage: "curl $HOST:$PORT/product/1".
	 *
	 * @param productId Id of the product
	 * @return the product, if found, else null
	 */
	@GetMapping(value = "/product/{productId}", produces = "application/json")
	Mono<Product> getProduct(@PathVariable("productId") int productId);
	
	  
	  Mono<Product> createProduct(Product body);
	  
	 
	  Mono<Void> deleteProduct(int productId);
	
}