package com.example.shipping;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.repository.CrudRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}

@Service
@RequiredArgsConstructor
class OrderService {

	private final KafkaTemplate kafkaTemplate;
	private final ShippingRepository shippingRepository;

	//topic name = prod.orders.placed
	@KafkaListener(topics = "earandil", groupId = "shipping-group")
	public void handleOrderPlacedEvent(OrderPlacedEvent event) {
		Shipping shipping = new Shipping();
		shipping.setOrderId(event.getOrderId());
		this.shippingRepository.save(shipping);
		this.kafkaTemplate.send("prod.orders.shipped", String.valueOf(shipping.getOrderId()), String.valueOf(shipping.getId()));
	}
}


@Data
@NoArgsConstructor
@AllArgsConstructor
class OrderPlacedEvent {

	private int orderId;
	private String product;
	private double price;
}

@Entity
@Data
class Shipping {
	@Id
	@GeneratedValue
	private Long id;
	private int orderId;
}

interface ShippingRepository extends CrudRepository<Shipping, Long> {

}