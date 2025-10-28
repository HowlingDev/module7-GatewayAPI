package com.example;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/apigateway")
public class ApiGatewayController {

    private final String USER_SERVICE_URL = "http://localhost:8080/users";

    private final String NOTIFICATION_SERVICE_URL = "http://localhost:8000//api/notifications";


}
