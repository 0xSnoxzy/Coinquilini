package it.coinquilini.controller;

import it.coinquilini.service.HouseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/houses")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"}, allowCredentials = "true")
public class HouseController {
    private final HouseService houses;

    public HouseController(HouseService houses) {
        this.houses = houses;
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, Boolean>> create(@RequestBody CreateRequest body, HttpServletRequest request) {
        houses.create(request.getSession(false), body.name(), body.emoji());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true));
    }

    @PostMapping("/join")
    public Map<String, Boolean> join(@RequestBody JoinRequest body, HttpServletRequest request) {
        houses.join(request.getSession(false), body.code());
        return Map.of("ok", true);
    }

    public record CreateRequest(String name, String emoji) {}
    public record JoinRequest(String code) {}
}
