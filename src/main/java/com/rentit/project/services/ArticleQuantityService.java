package com.rentit.project.services;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rentit.project.models.ArticleQuantityEntity;
import com.rentit.project.models.InvoiceEntity;
import com.rentit.project.models.RentalEntity;
import com.rentit.project.models.UserEntity;
import com.rentit.project.pojo.query.CustomPojoReturn;
import com.rentit.project.pojo.response.MessageResponse;
import com.rentit.project.repositories.ArticleQuantityRepository;

@Service
public class ArticleQuantityService {

	@Autowired
	private MailService mailService;

	@Autowired
	private ArticleQuantityRepository articleQuantityRepository;

	@Autowired
	private InvoiceService invoiceService;

	@Autowired
	private UserService userService;

	@Autowired
	private ArticleService articleService;

	@Autowired
	private RentalService rentalService;

	// Viele Artikel
	public ResponseEntity<MessageResponse> addArticleQuantity(List<ArticleQuantityEntity> quantityEntity,
			String authHeader) {

		StringBuilder articles = new StringBuilder();
		UserEntity user = userService.getUserFromToken(authHeader);

		double subTotal = 0.0; // subTotal for quantityEntity
		double totalPrice = 0.0; // totalPrice for rental

		// invoiceEntity
		InvoiceEntity invoiceEntity = new InvoiceEntity();
		invoiceEntity.setInvoiceDate(LocalDateTime.now());
		invoiceEntity.setInvoiceNumber(getRandomNumberInRange(100000, 1000000000));
		invoiceService.addInvoice(invoiceEntity);

		// rentalEntity
		RentalEntity rentalEntity = new RentalEntity();
		rentalEntity.setInvoice(invoiceEntity);
		rentalEntity.setRentDate(LocalDateTime.now());
		rentalEntity.setUsers(user);

		// hole ArticleId von quantityEntity
		// hole article and price
		// Rechnung totalPrice und subTotal
		for (int i = 0; i < quantityEntity.size(); i++) {
			// subTotal
			// Difference Date-Days
			long diffDay = ChronoUnit.DAYS.between(quantityEntity.get(i).getRentalDate(),
					quantityEntity.get(i).getReturnDate());
			// Price * Quantity * Difference Date-Days
			subTotal = quantityEntity.get(i).getQuantity()
					* articleService.getArticle(quantityEntity.get(i).getArticle().getArticleId()).getPrice() * diffDay;

			quantityEntity.get(i).setSubTotal(subTotal);

			// total
			totalPrice += subTotal;

			articles.append(i + 1 + " : "
					+ articleService.getArticle(quantityEntity.get(i).getArticle().getArticleId()).getName()
					+ " | Rent Date: "
					+ quantityEntity.get(i).getRentalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
					+ " | Return Date: "
					+ quantityEntity.get(i).getReturnDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
					+ " | Quantity: " + quantityEntity.get(i).getQuantity() + "\n");
		}

		rentalEntity.setTotalPrice(totalPrice);
		rentalService.addRental(rentalEntity);

		// update quantityEntity
		for (int i = 0; i < quantityEntity.size(); i++) {
			quantityEntity.get(i).setRental(rentalEntity);
			quantityEntity.get(i).setReturned(false);
			articleQuantityRepository.save(quantityEntity.get(i));
		}

		// email
		String text = "Congratulations!!! \n\n\n Dear " + user.getLastname()
				+ ",\n\n\n Articles was rented successfully !!! " + "\n\n invoice Nr: "
				+ invoiceEntity.getInvoiceNumber() + "\n\n Date: "
				+ invoiceEntity.getInvoiceDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "\n\n TotalPrice:"
				+ rentalEntity.getTotalPrice() + "\n\n Return Date:" + "\n\nArticles:\n" + articles.toString()
				+ " \n\n https://rentit24.tech/  \n\n\n\n Kind Regards\n\n\nBest Team JEE 2021";
		String subject = "Successfully rented!!!";
		mailService.sendMail(text, user, subject);

		return ResponseEntity.ok().body(new MessageResponse("Successfully returned"));
	}

	// One ArticleQuantity
	public ResponseEntity<MessageResponse> addArticleQuantity(ArticleQuantityEntity quantityEntity) {
		articleQuantityRepository.save(quantityEntity);
		return ResponseEntity.ok().body(new MessageResponse("Successfully added"));
	}

	public ArticleQuantityEntity getArticleQuantity(Long id) {
		return articleQuantityRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	public List<ArticleQuantityEntity> getAllArticleQuantitys() {
		return articleQuantityRepository.findAll();
	}

	public ResponseEntity<MessageResponse> deleteArticleQuantity(Long id) {
		ArticleQuantityEntity articleQuantity = getArticleQuantity(id);
		articleQuantityRepository.delete(articleQuantity);
		return ResponseEntity.ok().body(new MessageResponse("Successfully deleted"));
	}

	public ArticleQuantityEntity updateArticleQuantities(ArticleQuantityEntity quantityEntity, long id) {
		ArticleQuantityEntity _quantityEntity = getArticleQuantity(id);
		_quantityEntity = quantityEntity;
		return articleQuantityRepository.save(_quantityEntity);
	}

	// public List<ArticleQuantityEntity>
	// updateArticleQuantities(List<ArticleQuantityEntity> list) {
	// return articleQuantityRepository.saveAll(list);
	// }

	@Transactional
	public List<CustomPojoReturn> getListRentalUser(Long userId) {
		return articleQuantityRepository.getListRentalUser(userId);
	}

	// call the function eceryDay at 08AM
	@Scheduled(cron = "0 8 * * * *")
	public void checkAllArticlesQuantities() {
		LocalDateTime date = LocalDateTime.now();

		List<ArticleQuantityEntity> quantities = articleQuantityRepository.findAll();
		for (int i = 0; i < quantities.size(); i++) {
			UserEntity user = quantities.get(i).getRental().getUsers();
			if (date.equals(quantities.get(i).getReturnDate().minusDays(1))) {
				String text = "reminder!!! \n\n\n Dear " + user.getLastname()
						+ ",\n\n\n Please pay attention! you must return the rented items tomorrow !!! \n\n\n"
						+ " \n\n https://rentit24.tech/  \n\n\n\n Kind Regards\n\n\nBest Team JEE 2021";
				String subject = "Reminder!!!";
				mailService.sendMail(text, user, subject);
			}
		}
	}

	public ResponseEntity<MessageResponse> returnArticle(Map<String, ArrayList<Long>> data, String authHeader) {
		UserEntity user = userService.getUserFromToken(authHeader);
		Boolean toLate = false;
		double price = 0.0, actuPrice = 0.0;

		StringBuilder returnArticles = new StringBuilder();

		// invoiceEntity
		InvoiceEntity invoiceEntity = new InvoiceEntity();
		invoiceEntity.setInvoiceDate(LocalDateTime.now());
		// invoiceEntity.setInvoiceNumber(Integer.parseInt(UUID.randomUUID().toString()));
		invoiceService.addInvoice(invoiceEntity);

		ArrayList<Long> ids = data.get("ids");
		for (int i = 0; i < ids.size(); i++) {

			ArticleQuantityEntity articleQuantityEntity = getArticleQuantity(ids.get(i));

			articleQuantityEntity.setReturned(true);

			articleQuantityEntity.setReturnedDate(LocalDateTime.now());

			toLate = LocalDateTime.now().isBefore(articleQuantityEntity.getReturnDate());

			returnArticles.append(i + 1 + " : "
					+ articleService.getArticle(getArticleQuantity(ids.get(i)).getArticle().getArticleId()).getName()
					+ " | Rent Date: "
					+ getArticleQuantity(ids.get(i)).getRentalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
					+ " | Return Date: "
					+ getArticleQuantity(ids.get(i)).getReturnDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
					+ " | Returned Date: "
					+ getArticleQuantity(ids.get(i)).getReturnedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
					+ " | Quantity: " + getArticleQuantity(ids.get(i)).getQuantity() + "\n");

			if (!toLate) {
				price = articleQuantityEntity.getSubTotal() * 1.3;
				actuPrice = price - articleQuantityEntity.getSubTotal();
				articleQuantityEntity.setSubTotal(price);
				String text = "Warning!!! \n\n\n Dear " + user.getLastname()
						+ ",\n\n\n Articles was to late returned !!! \n\n\n You must pay in addition: \n " + actuPrice
						+ "\n\n Articles: \n" + returnArticles.toString()
						+ " \n\n https://rentit24.tech/  \n\n\n\n Kind Regards\n\n\nBest Team JEE 2021";
				String subject = "Successfully returned!!!";
				mailService.sendMail(text, user, subject);
			} else {
				String text = "Congratulations!!! \n\n\n Dear " + user.getLastname()
						+ ",\n\n\n Articles was returned successfully !!! \n\n\n " + "\n\n Articles: \n"
						+ returnArticles.toString()
						+ " \n\n https://rentit24.tech/  \n\n\n\n Kind Regards\n\n\nBest Team JEE 2021";
				String subject = "Successfully returned!!!";
				mailService.sendMail(text, user, subject);
			}

			addArticleQuantity(articleQuantityEntity);
		}

		return ResponseEntity.ok().body(new MessageResponse("Successfully returned"));
	}

	private static int getRandomNumberInRange(int min, int max) {
		Random r = new Random();
		return r.ints(min, (max + 1)).limit(1).findFirst().getAsInt();
	}

}
