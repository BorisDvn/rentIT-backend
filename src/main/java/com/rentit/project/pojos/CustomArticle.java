package com.rentit.project.pojos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomArticle {

	private long id;
	private String name;
	private String description;
	private int stockLevel;
	private double price;
	private String imageLink;
}