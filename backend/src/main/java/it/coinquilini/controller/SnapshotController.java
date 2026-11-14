package it.coinquilini.controller;

import it.coinquilini.model.Snapshot;
import it.coinquilini.service.SnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"}, allowCredentials = "true")
public class SnapshotController {
    private final SnapshotService snapshots;

    public SnapshotController(SnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @GetMapping("/api/snapshot")
    public Snapshot snapshot(HttpServletRequest request) {
        return snapshots.load(request.getSession(false));
    }
}
