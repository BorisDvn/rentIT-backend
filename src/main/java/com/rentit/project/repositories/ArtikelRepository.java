package com.rentit.project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.rentit.project.models.ArticleEntity;

@Repository
public interface ArtikelRepository extends JpaRepository<ArticleEntity, Long> {

}