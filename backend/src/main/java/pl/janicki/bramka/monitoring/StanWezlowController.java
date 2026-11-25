package pl.janicki.bramka.monitoring;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wezly")
@PreAuthorize("hasRole('ADMIN')")
public class StanWezlowController {

    private final StanWezlowService stanWezlow;

    public StanWezlowController(StanWezlowService stanWezlow) {
        this.stanWezlow = stanWezlow;
    }

    @GetMapping("/stan")
    public StanWezlowService.StanWezlow stan() {
        return stanWezlow.stan();
    }
}
