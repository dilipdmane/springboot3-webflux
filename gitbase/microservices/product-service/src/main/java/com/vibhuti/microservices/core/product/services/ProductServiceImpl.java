package com.vibhuti.microservices.core.product.services;

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

@RestController
public class ProductServiceImpl implements ProductService {
	private static final Logger LOG = LoggerFactory.getLogger(ProductServiceImpl.class);
	
	private final ServiceUtil serviceUtil;
	private final ProductRepository productRepository;
	private final ProductMapper productMapper;

	//@Autowired
	public ProductServiceImpl(ServiceUtil serviceUtil,ProductRepository productRepository,ProductMapper productMapper) {
		this.serviceUtil = serviceUtil;
		this.productRepository = productRepository;
		this.productMapper = productMapper;
	}

	@Override
	public Product getProduct(int productId) {
		if (productId < 1) {
			throw new InvalidInputException("Invalid productId: " + productId);
		}
		ProductEntity productEntity = this.productRepository.findByProductId(productId).orElseThrow(
				()-> new NotFoundException("No product found for productId: " + productId));
		Product product = this.productMapper.entityToApi(productEntity);
		product.setServiceAddress(this.serviceUtil.getServiceAddress());
		return product;
	}

	@Override
	public Product createProduct(Product body) {
		try {
			ProductEntity productEntity = this.productMapper.apiToEntity(body);
			ProductEntity savedProductEntity = this.productRepository.save(productEntity);
		    LOG.debug("createProduct: entity created for productId: {}", body.getProductId());
			return this.productMapper.entityToApi(savedProductEntity);
		} catch (DuplicateKeyException e) {
			throw new InvalidInputException("Duplicate key, Product Id: " + 
			        body.getProductId());
		}
		
	}

	@Override
	public void deleteProduct(int productId) {
		LOG.debug("deleteProduct: tries to delete an entity with productId: {}", productId);
	    this.productRepository.findByProductId(productId).ifPresent(e -> this.productRepository.delete(e));	
	}
}