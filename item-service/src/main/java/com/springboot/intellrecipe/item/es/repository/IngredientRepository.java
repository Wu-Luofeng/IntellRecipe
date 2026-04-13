package com.springboot.intellrecipe.item.es.repository;

import com.springboot.intellrecipe.item.es.document.IngredientDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface IngredientRepository extends ElasticsearchRepository<IngredientDoc, Long> {
}
