package br.com.icev.aed.controller;

import br.com.icev.aed.service.CalculoScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/score")
@CrossOrigin(origins = "*")
public class ScoreController {

    @Autowired
    private CalculoScoreService calculoScoreService;

    /**
     * Calcula o score final de todos os trabalhos
     * GET /api/score/calcular
     */
    @GetMapping("/calcular")
    public ResponseEntity<Map<String, Object>> calcularScoreFinal() {
        try {
            Map<String, Object> resultado = calculoScoreService.calcularScoreFinal();
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("erro", e.getMessage()));
        }
    }
}
