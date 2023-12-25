package com.vibhuti.microservices.core.product.services;

import static java.util.logging.Level.FINE;
import org.slf4j.Logger;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestController;

import com.vibhuti.microservices.api.core.product.Product;
import com.vibhuti.microservices.api.core.product.ProductService;
import com.vibhuti.microservices.core.product.persistence.ProductEntity;
import com.vibhuti.microservices.core.product.persistence.ProductRepository;
import com.vibhuti.microservices.exception.InvalidInputException;
import com.vibhuti.microservices.exception.NotFoundException;
import com.vibhuti.microservices.util.ServiceUtil;

import reactor.core.publisher.Mono;

@RestController
public class ProductServiceImpl implements ProductService {
	private static final Logger LOG = LoggerFactory.getLogger(ProductServiceImpl.class);

	private final ServiceUtil serviceUtil;
	private final ProductRepository productRepository;
	private final ProductMapper productMapper;

	// @Autowired
	public ProductServiceImpl(ServiceUtil serviceUtil, ProductRepository productRepository,
			ProductMapper productMapper) {
		this.serviceUtil = serviceUtil;
		this.productRepository = productRepository;
		this.productMapper = productMapper;
	}

	@Override
	public Mono<Product> getProduct(int productId) {
		if (productId < 1) {
			throw new InvalidInputException("Invalid productId: " + productId);
		}
		return this.productRepository.findByProductId(productId)
				.switchIfEmpty(Mono.error(new NotFoundException("No product found for productId: " + productId)))
				.log(LOG.getName(), FINE).map(e -> this.productMapper.entityToApi(e)).map(e -> setServiceAddres(e));
	}

	private Product setServiceAddres(Product e) {
		// TODO Auto-generated method stub
		e.setServiceAddress(this.serviceUtil.getServiceAddress());
		return e;
	}

	@Override
	public Mono<Product> createProduct(Product body) {
		if (body.getProductId() < 1) {
			throw new InvalidInputException("Invalid productId: " + body.getProductId());
		}
		ProductEntity productEntity = this.productMapper.apiToEntity(body);
		Mono<Product> product = this.productRepository.save(productEntity).log(LOG.getName(), FINE)
				.onErrorMap(DuplicateKeyException.class,
						ex -> new InvalidInputException("Duplicate key, Product Id: " + body.getProductId()))
				.map(e -> this.productMapper.entityToApi(e));
		return product;
	}

	@Override
	public Mono<Void> deleteProduct(int productId) {
		if (productId < 1) {
			throw new InvalidInputException("Invalid productId: " + productId);
		}
		LOG.debug("deleteProduct: tries to delete an entity with productId: {}", productId);
		return this.productRepository.findByProductId(productId).log(LOG.getName(), FINE)
				.map(e -> this.productRepository.delete(e)).flatMap(e -> e);
	}
}