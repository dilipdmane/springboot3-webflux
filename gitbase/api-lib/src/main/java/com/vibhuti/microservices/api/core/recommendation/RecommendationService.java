package com.vibhuti.microservices.api.core.recommendation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RecommendationService {

  /**
   * Sample usage: "curl $HOST:$PORT/recommendation?productId=1".
   *
   * @param productId Id of the product
   * @return the recommendations of the product
   */
  @GetMapping(
    value = "/recommendation",
    produces = "application/json")
  Flux<Recommendation> getRecommendations(
    @RequestParam(value = "productId", required = true) int productId);
 
  Mono<Recommendation> createRecommendation(Recommendation body);
  
   Mono<Void> deleteRecommendations(int productId);
}