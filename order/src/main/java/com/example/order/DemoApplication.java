package com.example.order;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@EnableFeignClients
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}

@RestController
@RequiredArgsConstructor
class OrderController {

	private final OrderService orderService;

	@PostMapping("/orders")
	@ResponseStatus(HttpStatus.CREATED)
	public void placeOrder(@RequestBody PlaceOrderRequest request) {
		this.orderService.placeOrder(request);
	}
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class PlaceOrderRequest {
	private String product;
	private double price;
}

@Service
@RequiredArgsConstructor
class OrderService {

	private final KafkaTemplate	kafkaTemplate;
	private final OrderRepository orderRepository;
	@Autowired
	private InventoryClient inventoryClient;

	public void placeOrder(PlaceOrderRequest request) {
		InventoryStatus status = inventoryClient.exists(request.getProduct());
		// todo : ControllerAdvice
		if (!status.isExists()) {
			throw new EntityNotFoundException("Product does not exist");
		}
		inventoryClient.exists(request.getProduct());
		//save into db
		Order order = new Order();
		order.setPrice(request.getPrice());
		order.setProduct(request.getProduct());
		order.setStatus(OrderStatus.PLACED.name());
		Order o = this.orderRepository.save(order);
		this.kafkaTemplate.send("earandil", String.valueOf(o.getId()), OrderPlacedEvent.builder()
				.orderId(o.getId().intValue())
				.product(request.getProduct())
				.price(request.getPrice())
				.build());

		//publish event

	}

	@KafkaListener(topics = "prod.orders.shipped", groupId = "order-group")
	public void handleOrderShippedEvent(String orderId) {
		this.orderRepository.findById(Long.valueOf(orderId)).ifPresent(order -> {
			order.setStatus(OrderStatus.SHIPPED.name());
			this.orderRepository.save(order);
		});
	}
}

@Data
@Builder
class OrderPlacedEvent {

	private int orderId;
	private String product;
	private double price;
}

interface OrderRepository extends CrudRepository<Order, Long> {

}

@Entity(name = "orders")
@Data
class Order {

	@Id
	@GeneratedValue
	private Long Id;

	private String product;
	private double price;
	private String status;

}

enum OrderStatus {
	PLACED,
	SHIPPED
}

@FeignClient(url = "http://localhost:8092", name = "inventories")
interface InventoryClient {

	@GetMapping("/inventories")
	InventoryStatus exists(@RequestParam("productId") String productId);
}

@Data
class InventoryStatus {
	private boolean exists;
}