package com.example;

import com.example.config.CircuitBreaker;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Setter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/apigateway")
public class ApiGatewayController {

    private static final Logger LOGGER = LogManager.getLogger(ApiGatewayController.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Setter
    @Value("${user.service.url}")
    private String userServiceUrl;

    @Setter
    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    private final RestTemplate restTemplate;

    private final CircuitBreaker userServiceCircuitBreaker;

    private final CircuitBreaker notificationServiceCircuitBreaker;

    @Autowired
    public ApiGatewayController(RestTemplate restTemplate, CircuitBreaker userServiceCircuitBreaker, CircuitBreaker notificationServiceCircuitBreaker) {
        this.restTemplate = restTemplate;
        this.userServiceCircuitBreaker = userServiceCircuitBreaker;
        this.notificationServiceCircuitBreaker = notificationServiceCircuitBreaker;
    }

    @Tag(name = "Сервис уведомлений", description = "Перенаправляет запросы пользователей в сервис уведомлений")
    @Operation(
            summary = "Отправка email пользователю",
            description = """
                    Позволяет отправить email пользователю. \n
                    Параметр emailDetails состоит из полей: \n
                    recipient - email получателя, тип - строка \n
                    message - текст сообщения, тип - строка \n
                    subject - тема сообщения, тип - строка \n
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email успешно отправлен"),
            @ApiResponse(responseCode = "503", description = "Сервис уведомлений временно недоступен")}
    )
    @PostMapping("/sendEmail")
    public ResponseEntity<?> sendEmail(@RequestBody Object emailDetails) {
        LOGGER.info("Вызван метод sendEmail с параметром emailDetails = {}", emailDetails.toString());
        if (!notificationServiceCircuitBreaker.isServiceAvailable()) {
            LOGGER.error("При вызове метода sendEmail оказалось, что сервис уведомлений недоступен");
            return ResponseEntity.status(503).body("Сервис уведомлений недоступен");
        }
        try {
            ResponseEntity<?> response = restTemplate.postForEntity(notificationServiceUrl + "/sendEmail", emailDetails, String.class);
            LOGGER.info("Email успешно отправлен");
            return response;
        } catch (Exception e) {
            LOGGER.error("sendEmail не удалось обратиться к сервису уведомлений, CircuitBreaker закрывает доступ к сервису уведомлений");
            notificationServiceCircuitBreaker.openCircuit();
            return ResponseEntity.status(503).body("Сервис уведомлений недоступен");
        }
    }

    @Tag(name = "Сервис пользователей", description = "Перенаправляет запросы пользователей в сервис пользователей")
    @Operation(
            summary = "Создание пользователя",
            description = "Позволяет создать нового пользователя. Подробнее: http://localhost:8080/swagger-ui/index.html"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Пользователь успешно создан"),
            @ApiResponse(responseCode = "400", description = "Данные введены некорректно"),
            @ApiResponse(responseCode = "500", description = "Нарушена уникальность email"),
            @ApiResponse(responseCode = "503", description = "Сервис пользователей временно недоступен")}
    )
    @PostMapping("/createUser")
    public ResponseEntity<?> createUser(@RequestBody Object userDto) throws JsonProcessingException {
        LOGGER.info("Вызван метод createUser с параметром userDto = {}", objectMapper.writeValueAsString(userDto));
        if (!userServiceCircuitBreaker.isServiceAvailable()) {
            LOGGER.error("При вызове метода createUser оказалось, что сервис пользователей недоступен");
            return ResponseEntity.status(503).body("Сервис пользователей недоступен");
        }
        try {
            ResponseEntity<?> response = restTemplate.postForEntity(userServiceUrl + "/create", userDto, Object.class);
            LOGGER.info("Пользователь успешно создан");
            return response;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            if (e.getStatusCode().is5xxServerError()) {
                LOGGER.warn("Пользователь не создан из-за нарушения уникальности email");
                return ResponseEntity.status(500).body("Такой email уже занят");
            } else {
                LOGGER.warn("Пользователь не создан из-за плохого запроса (не хватает данных либо они не удовлетворяют ограничениям)");
                return ResponseEntity.status(400).body("Проверьте, что данные отправлены полностью и удовлетворяют ограничениям");
            }
        } catch (Exception e) {
            LOGGER.error("createUser не удалось обратиться к сервису пользователей, CircuitBreaker закрывает доступ к сервису пользователей");
            userServiceCircuitBreaker.openCircuit();
            return ResponseEntity.status(503).body("Сервис пользователей недоступен из-за сбоя");
        }
    }

    @Tag(name = "Сервис пользователей", description = "Перенаправляет запросы пользователей в сервис пользователей")
    @Operation(
            summary = "Получение пользователя по ID",
            description = "Позволяет найти пользователя по его идентификатору. Подробнее: http://localhost:8080/swagger-ui/index.html"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно найден"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "503", description = "Сервис пользователей временно недоступен")}
    )
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        LOGGER.info("Вызван метод getUserById с параметром id = {}", id);
        if (!userServiceCircuitBreaker.isServiceAvailable()) {
            LOGGER.error("При вызове метода getUserById оказалось, что сервис пользователей недоступен");
            return ResponseEntity.status(503).body("Сервис пользователей недоступен");
        }
        try {
            ResponseEntity<?> response = restTemplate.getForEntity(userServiceUrl + "/" + id, Object.class);
            LOGGER.info("Пользователь успешно найден");
            return response;
        } catch (HttpClientErrorException e) {
            LOGGER.warn("Пользователь не найден");
            return ResponseEntity.status(404).body("Пользователь не найден");
        } catch (Exception e) {
            LOGGER.error("getUserById не удалось обратиться к сервису пользователей, CircuitBreaker закрывает доступ к сервису пользователей");
            userServiceCircuitBreaker.openCircuit();
            return ResponseEntity.status(503).body("Сервис пользователей недоступен из-за сбоя");
        }
    }

    @Tag(name = "Сервис пользователей", description = "Перенаправляет запросы пользователей в сервис пользователей")
    @Operation(
            summary = "Получение списка всех пользователей",
            description = "Позволяет найти всех пользователей. Подробнее: http://localhost:8080/swagger-ui/index.html"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список пользователей получен"),
            @ApiResponse(responseCode = "503", description = "Сервис пользователей временно недоступен")}
    )
    @GetMapping("/users/all")
    public ResponseEntity<?> getAllUsers() {
        LOGGER.info("Вызван метод getAllUsers");
        if (!userServiceCircuitBreaker.isServiceAvailable()) {
            LOGGER.error("При вызове метода getAllUsers оказалось, что сервис пользователей недоступен");
            return ResponseEntity.status(503).body("Сервис пользователей недоступен");
        }
        try {
            ResponseEntity<?> response = restTemplate.exchange(userServiceUrl + "/all",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    });
            LOGGER.info("Все пользователи успешно найдены");
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            LOGGER.error("getAllUsers не удалось обратиться к сервису пользователей, CircuitBreaker закрывает доступ к сервису пользователей");
            userServiceCircuitBreaker.openCircuit();
            return ResponseEntity.status(503).body("Сервис пользователей недоступен из-за сбоя");
        }
    }

    @Tag(name = "Сервис пользователей", description = "Перенаправляет запросы пользователей в сервис пользователей")
    @Operation(
            summary = "Обновление пользователя",
            description = "Позволяет обновить информацию о пользователе с заданным ID. Подробнее: http://localhost:8080/swagger-ui/index.html"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Обновление пользователя прошло успешно"),
            @ApiResponse(responseCode = "400", description = "Данные введены некорректно"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "503", description = "Сервис пользователей временно недоступен")}
    )
    @PutMapping("users/update/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Object userDto) throws JsonProcessingException {
        LOGGER.info("Вызван метод updateUser с параметрами id = {}, userDto = {}", id, objectMapper.writeValueAsString(userDto));
        if (!userServiceCircuitBreaker.isServiceAvailable()) {
            LOGGER.error("При вызове метода updateUser оказалось, что сервис пользователей недоступен");
            return ResponseEntity.status(503).body("Сервис пользователей недоступен");
        }
        try {
            restTemplate.put(userServiceUrl + "/update/" + id, userDto);
            LOGGER.info("Пользователь успешно обновлен");
            return ResponseEntity.ok().build();
        } catch (HttpClientErrorException.NotFound e) {
            LOGGER.warn("Не удалось обновить пользователя, так как он не найден");
            return ResponseEntity.status(404).body("Пользователь не найден");
        } catch (HttpClientErrorException.BadRequest e) {
            LOGGER.warn("Пользователь не обновлен из-за плохого запроса (не хватает данных либо они не удовлетворяют ограничениям)");
            return ResponseEntity.status(400).body("Проверьте, что данные отправлены полностью и удовлетворяют ограничениям");
        } catch (Exception e) {
            LOGGER.error("updateUser не удалось обратиться к сервису пользователей, CircuitBreaker закрывает доступ к сервису пользователей");
            userServiceCircuitBreaker.openCircuit();
            return ResponseEntity.status(503).body("Сервис пользователей недоступен из-за сбоя");
        }
    }

    @Tag(name = "Сервис пользователей", description = "Перенаправляет запросы пользователей в сервис пользователей")
    @Operation(
            summary = "Удаление пользователя",
            description = "Позволяет удалить пользователя с заданным ID. Подробнее: http://localhost:8080/swagger-ui/index.html"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Удаление пользователя прошло успешно"),
            @ApiResponse(responseCode = "400", description = "id должен быть не меньше 1"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")}
    )
    @DeleteMapping("/users/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        LOGGER.info("Вызван метод deleteUser с параметрами id = {}", id);
        if (!userServiceCircuitBreaker.isServiceAvailable()) {
            LOGGER.error("При вызове метода deleteUser оказалось, что сервис пользователей недоступен");
            return ResponseEntity.status(503).body("Сервис пользователей недоступен");
        }
        try {
            restTemplate.delete(userServiceUrl + "/delete/" + id);
            LOGGER.info("Пользователь успешно удален");
            return ResponseEntity.ok().build();
        } catch (HttpClientErrorException.BadRequest e) {
            return ResponseEntity.status(400).body("id олжен быть не меньше 1");
        } catch (Exception e) {
            LOGGER.error("deleteUser не удалось обратиться к сервису пользователей, CircuitBreaker закрывает доступ к сервису пользователей");
            userServiceCircuitBreaker.openCircuit();
            return ResponseEntity.status(503).body("Сервис пользователей недоступен из-за сбоя");
        }
    }
}
