package br.com.icev.aed.service;

import br.edu.icev.aed.forense.Alerta;
import br.edu.icev.aed.forense.AnaliseForenseAvancada;
import br.com.icev.aed.entity.TestesTrabalho;
import br.com.icev.aed.entity.Trabalho;
import br.com.icev.aed.repository.TestesTrabalhoRepository;
import br.com.icev.aed.repository.TrabalhoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class TestesEficienciaService {

    private static final Logger logger = LoggerFactory.getLogger(TestesEficienciaService.class);

    @Autowired
    private TrabalhoRepository trabalhoRepository;

    @Autowired
    private TestesTrabalhoRepository testesTrabalhoRepository;

    // Número de execuções para cada teste
    private static final int NUMERO_EXECUCOES = 50;

    // Thresholds de performance em milissegundos (tempo máximo aceitável)
    private static final double THRESHOLD_DESAFIO_1 = 50.0;   // Set - 50ms
    private static final double THRESHOLD_DESAFIO_2 = 100.0;  // LinkedList - 100ms
    private static final double THRESHOLD_DESAFIO_3 = 150.0;  // PriorityQueue - 150ms
    private static final double THRESHOLD_DESAFIO_4 = 200.0;  // Map - 200ms
    private static final double THRESHOLD_DESAFIO_5 = 300.0;  // BFS - 300ms

    // Pontuação máxima por desafio
    private static final double PONTOS_POR_DESAFIO = 8.0;

    /**
     * Executa testes de eficiência para um trabalho específico
     */
    public Map<String, Object> executarTestesEficiencia(Long trabalhoId) {
        Optional<Trabalho> trabalhoOpt = trabalhoRepository.findById(trabalhoId);
        if (!trabalhoOpt.isPresent()) {
            throw new RuntimeException("Trabalho não encontrado: " + trabalhoId);
        }

        Trabalho trabalho = trabalhoOpt.get();
        logger.info("Iniciando testes de eficiência para trabalho ID: {}", trabalhoId);

        // Criar registro de teste
        TestesTrabalho testeExecucao = new TestesTrabalho();
        testeExecucao.setTrabalho(trabalho);
        testeExecucao.setHorarioInicio(LocalDateTime.now());
        testeExecucao.setStatusExecucao("RUNNING");
        testeExecucao.setCategoria("TESTE_EFICIENCIA");
        testeExecucao.setQuantidadeTestesUnitarios(NUMERO_EXECUCOES * 5); // 200 execuções × 5 desafios
        testesTrabalhoRepository.save(testeExecucao);

        StringBuilder resultado = new StringBuilder();
        resultado.append("=== TESTES DE EFICIÊNCIA ===\n");
        resultado.append(String.format("Trabalho ID: %d\n", trabalhoId));
        resultado.append(String.format("Alunos: %s", trabalho.getMatriculaAluno1()));
        if (trabalho.getMatriculaAluno2() != null) resultado.append(", ").append(trabalho.getMatriculaAluno2());
        if (trabalho.getMatriculaAluno3() != null) resultado.append(", ").append(trabalho.getMatriculaAluno3());
        resultado.append("\n");
        resultado.append(String.format("Classe: %s\n", trabalho.getClasseQueImplementa()));
        resultado.append(String.format("Execuções por desafio: %d\n\n", NUMERO_EXECUCOES));

        Map<String, Double> pontosPorDesafio = new LinkedHashMap<>();

        try {
            // Carregar o JAR e instanciar a classe
            File jarFile = new File(trabalho.getCaminhoParaJar());
            URL jarUrl = jarFile.toURI().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader());

            Class<?> classe = Class.forName(trabalho.getClasseQueImplementa(), true, classLoader);
            AnaliseForenseAvancada instancia = (AnaliseForenseAvancada) classe.getDeclaredConstructor().newInstance();

            // Caminho do arquivo de logs
            String caminhoArquivo = new File("../arquivo_logs.csv").getAbsolutePath();

            // DESAFIO 1: Encontrar Sessões Inválidas
            try {
                resultado.append("\n=== DESAFIO 1: Encontrar Sessões Inválidas (200 execuções) ===\n");
                EstatisticasExecucao stats1 = executarTesteDesafio1(instancia, caminhoArquivo, trabalho);
                double pontos1 = calcularPontuacao(stats1.getTempoMedio(), THRESHOLD_DESAFIO_1);
                pontosPorDesafio.put("Desafio 1", pontos1);
                
                resultado.append(formatarEstatisticas(stats1, THRESHOLD_DESAFIO_1, pontos1));
            } catch (Exception e) {
                resultado.append("❌ ERRO: ").append(e.getMessage()).append("\n");
                pontosPorDesafio.put("Desafio 1", 0.0);
            }

            // DESAFIO 2: Reconstruir Linha do Tempo
            try {
                resultado.append("\n=== DESAFIO 2: Reconstruir Linha do Tempo (200 execuções) ===\n");
                EstatisticasExecucao stats2 = executarTesteDesafio2(instancia, caminhoArquivo, trabalho);
                double pontos2 = calcularPontuacao(stats2.getTempoMedio(), THRESHOLD_DESAFIO_2);
                pontosPorDesafio.put("Desafio 2", pontos2);
                
                resultado.append(formatarEstatisticas(stats2, THRESHOLD_DESAFIO_2, pontos2));
            } catch (Exception e) {
                resultado.append("❌ ERRO: ").append(e.getMessage()).append("\n");
                pontosPorDesafio.put("Desafio 2", 0.0);
            }

            // DESAFIO 3: Priorizar Alertas
            try {
                resultado.append("\n=== DESAFIO 3: Priorizar Alertas (200 execuções) ===\n");
                EstatisticasExecucao stats3 = executarTesteDesafio3(instancia, caminhoArquivo, trabalho);
                double pontos3 = calcularPontuacao(stats3.getTempoMedio(), THRESHOLD_DESAFIO_3);
                pontosPorDesafio.put("Desafio 3", pontos3);
                
                resultado.append(formatarEstatisticas(stats3, THRESHOLD_DESAFIO_3, pontos3));
            } catch (Exception e) {
                resultado.append("❌ ERRO: ").append(e.getMessage()).append("\n");
                pontosPorDesafio.put("Desafio 3", 0.0);
            }

            // DESAFIO 4: Encontrar Picos de Transferência
            try {
                resultado.append("\n=== DESAFIO 4: Encontrar Picos de Transferência (200 execuções) ===\n");
                EstatisticasExecucao stats4 = executarTesteDesafio4(instancia, caminhoArquivo, trabalho);
                double pontos4 = calcularPontuacao(stats4.getTempoMedio(), THRESHOLD_DESAFIO_4);
                pontosPorDesafio.put("Desafio 4", pontos4);
                
                resultado.append(formatarEstatisticas(stats4, THRESHOLD_DESAFIO_4, pontos4));
            } catch (Exception e) {
                resultado.append("❌ ERRO: ").append(e.getMessage()).append("\n");
                pontosPorDesafio.put("Desafio 4", 0.0);
            }

            // DESAFIO 5: Rastrear Contaminação
            try {
                resultado.append("\n=== DESAFIO 5: Rastrear Contaminação (200 execuções) ===\n");
                EstatisticasExecucao stats5 = executarTesteDesafio5(instancia, caminhoArquivo, trabalho);
                double pontos5 = calcularPontuacao(stats5.getTempoMedio(), THRESHOLD_DESAFIO_5);
                pontosPorDesafio.put("Desafio 5", pontos5);
                
                resultado.append(formatarEstatisticas(stats5, THRESHOLD_DESAFIO_5, pontos5));
            } catch (Exception e) {
                resultado.append("❌ ERRO: ").append(e.getMessage()).append("\n");
                pontosPorDesafio.put("Desafio 5", 0.0);
            }

            classLoader.close();

        } catch (Exception e) {
            logger.error("Erro ao executar testes de eficiência", e);
            resultado.append("\n❌ ERRO GERAL: ").append(e.getMessage()).append("\n");
        }

        // Calcular pontuação total
        double pontuacaoTotal = pontosPorDesafio.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        // Adicionar resumo
        resultado.append("\n=== RESUMO DE PONTUAÇÃO ===\n");
        pontosPorDesafio.forEach((desafio, pontos) -> 
            resultado.append(String.format("%s: %.2f pontos\n", desafio, pontos))
        );
        resultado.append(String.format("\nPONTUAÇÃO TOTAL: %.2f/40.00 pontos\n", pontuacaoTotal));

        // Atualizar registro de teste
        testeExecucao.setHorarioFim(LocalDateTime.now());
        testeExecucao.setStatusExecucao("COMPLETED");
        testeExecucao.setPontuacao(pontuacaoTotal);
        testeExecucao.setResultado(resultado.toString());
        testesTrabalhoRepository.save(testeExecucao);

        logger.info("Testes de eficiência concluídos. Pontuação: {}/40.00", pontuacaoTotal);

        // Preparar resposta
        Map<String, Object> response = new HashMap<>();
        response.put("trabalhoId", trabalhoId);
        response.put("pontuacao", pontuacaoTotal);
        response.put("pontosPorDesafio", pontosPorDesafio);
        response.put("resultado", resultado.toString());
        response.put("execucoesRealizadas", NUMERO_EXECUCOES * 5);

        return response;
    }

    /**
     * Executa testes de eficiência para todos os trabalhos
     */
    public List<Map<String, Object>> executarTestesEficienciaTodos() {
        List<Trabalho> trabalhos = trabalhoRepository.findAll();
        List<Map<String, Object>> resultados = new ArrayList<>();

        for (Trabalho trabalho : trabalhos) {
            try {
                Map<String, Object> resultado = executarTestesEficiencia(trabalho.getId());
                resultados.add(resultado);
            } catch (Exception e) {
                logger.error("Erro ao executar testes de eficiência para trabalho {}", trabalho.getId(), e);
                Map<String, Object> erro = new HashMap<>();
                erro.put("trabalhoId", trabalho.getId());
                erro.put("erro", e.getMessage());
                resultados.add(erro);
            }
        }

        return resultados;
    }

    /**
     * Executa o Desafio 1 múltiplas vezes e coleta estatísticas
     */
    private EstatisticasExecucao executarTesteDesafio1(AnaliseForenseAvancada instancia, String caminhoArquivo, Trabalho trabalho) throws Exception {
        List<Long> tempos = new ArrayList<>();
        Object resultado = null;

        for (int i = 0; i < NUMERO_EXECUCOES; i++) {
            LocalDateTime inicio = LocalDateTime.now();
            long tempoInicio = System.nanoTime();
            
            resultado = instancia.encontrarSessoesInvalidas(caminhoArquivo);
            
            long tempoFim = System.nanoTime();
            LocalDateTime fim = LocalDateTime.now();
            double tempoMs = (tempoFim - tempoInicio) / 1_000_000.0;
            tempos.add(tempoFim - tempoInicio);
            
            // Salvar registro individual no banco
            TestesTrabalho testeIndividual = new TestesTrabalho();
            testeIndividual.setTrabalho(trabalho);
            testeIndividual.setHorarioInicio(inicio);
            testeIndividual.setHorarioFim(fim);
            testeIndividual.setStatusExecucao("COMPLETED");
            testeIndividual.setCategoria("EFICIENCIA_DESAFIO_1");
            testeIndividual.setQuantidadeTestesUnitarios(i + 1); // Número da execução
            testeIndividual.setPontuacao(tempoMs);
            testeIndividual.setResultado(String.format("Execução %d - Tempo: %.2f ms", i + 1, tempoMs));
            testesTrabalhoRepository.save(testeIndividual);
            
            // Sleep de 2 segundos entre execuções
            if (i < NUMERO_EXECUCOES - 1) {
                Thread.sleep(2000);
            }
        }

        return calcularEstatisticas(tempos, resultado);
    }

    /**
     * Executa o Desafio 2 múltiplas vezes e coleta estatísticas
     */
    private EstatisticasExecucao executarTesteDesafio2(AnaliseForenseAvancada instancia, String caminhoArquivo, Trabalho trabalho) throws Exception {
        List<Long> tempos = new ArrayList<>();
        Object resultado = null;

        for (int i = 0; i < NUMERO_EXECUCOES; i++) {
            LocalDateTime inicio = LocalDateTime.now();
            long tempoInicio = System.nanoTime();
            
            resultado = instancia.reconstruirLinhaTempo(caminhoArquivo, "alice");
            
            long tempoFim = System.nanoTime();
            LocalDateTime fim = LocalDateTime.now();
            double tempoMs = (tempoFim - tempoInicio) / 1_000_000.0;
            tempos.add(tempoFim - tempoInicio);
            
            // Salvar registro individual no banco
            TestesTrabalho testeIndividual = new TestesTrabalho();
            testeIndividual.setTrabalho(trabalho);
            testeIndividual.setHorarioInicio(inicio);
            testeIndividual.setHorarioFim(fim);
            testeIndividual.setStatusExecucao("COMPLETED");
            testeIndividual.setCategoria("EFICIENCIA_DESAFIO_2");
            testeIndividual.setQuantidadeTestesUnitarios(i + 1);
            testeIndividual.setPontuacao(tempoMs);
            testeIndividual.setResultado(String.format("Execução %d - Tempo: %.2f ms", i + 1, tempoMs));
            testesTrabalhoRepository.save(testeIndividual);
            
            // Sleep de 2 segundos entre execuções
            if (i < NUMERO_EXECUCOES - 1) {
                Thread.sleep(2000);
            }
        }

        return calcularEstatisticas(tempos, resultado);
    }

    /**
     * Executa o Desafio 3 múltiplas vezes e coleta estatísticas
     */
    private EstatisticasExecucao executarTesteDesafio3(AnaliseForenseAvancada instancia, String caminhoArquivo, Trabalho trabalho) throws Exception {
        List<Long> tempos = new ArrayList<>();
        Object resultado = null;

        for (int i = 0; i < NUMERO_EXECUCOES; i++) {
            LocalDateTime inicio = LocalDateTime.now();
            long tempoInicio = System.nanoTime();
            
            resultado = instancia.priorizarAlertas(caminhoArquivo, 5);
            
            long tempoFim = System.nanoTime();
            LocalDateTime fim = LocalDateTime.now();
            double tempoMs = (tempoFim - tempoInicio) / 1_000_000.0;
            tempos.add(tempoFim - tempoInicio);
            
            // Salvar registro individual no banco
            TestesTrabalho testeIndividual = new TestesTrabalho();
            testeIndividual.setTrabalho(trabalho);
            testeIndividual.setHorarioInicio(inicio);
            testeIndividual.setHorarioFim(fim);
            testeIndividual.setStatusExecucao("COMPLETED");
            testeIndividual.setCategoria("EFICIENCIA_DESAFIO_3");
            testeIndividual.setQuantidadeTestesUnitarios(i + 1);
            testeIndividual.setPontuacao(tempoMs);
            testeIndividual.setResultado(String.format("Execução %d - Tempo: %.2f ms", i + 1, tempoMs));
            testesTrabalhoRepository.save(testeIndividual);
            
            // Sleep de 2 segundos entre execuções
            if (i < NUMERO_EXECUCOES - 1) {
                Thread.sleep(2000);
            }
        }

        return calcularEstatisticas(tempos, resultado);
    }

    /**
     * Executa o Desafio 4 múltiplas vezes e coleta estatísticas
     */
    private EstatisticasExecucao executarTesteDesafio4(AnaliseForenseAvancada instancia, String caminhoArquivo, Trabalho trabalho) throws Exception {
        List<Long> tempos = new ArrayList<>();
        Object resultado = null;

        for (int i = 0; i < NUMERO_EXECUCOES; i++) {
            LocalDateTime inicio = LocalDateTime.now();
            long tempoInicio = System.nanoTime();
            
            resultado = instancia.encontrarPicosTransferencia(caminhoArquivo);
            
            long tempoFim = System.nanoTime();
            LocalDateTime fim = LocalDateTime.now();
            double tempoMs = (tempoFim - tempoInicio) / 1_000_000.0;
            tempos.add(tempoFim - tempoInicio);
            
            // Salvar registro individual no banco
            TestesTrabalho testeIndividual = new TestesTrabalho();
            testeIndividual.setTrabalho(trabalho);
            testeIndividual.setHorarioInicio(inicio);
            testeIndividual.setHorarioFim(fim);
            testeIndividual.setStatusExecucao("COMPLETED");
            testeIndividual.setCategoria("EFICIENCIA_DESAFIO_4");
            testeIndividual.setQuantidadeTestesUnitarios(i + 1);
            testeIndividual.setPontuacao(tempoMs);
            testeIndividual.setResultado(String.format("Execução %d - Tempo: %.2f ms", i + 1, tempoMs));
            testesTrabalhoRepository.save(testeIndividual);
            
            // Sleep de 2 segundos entre execuções
            if (i < NUMERO_EXECUCOES - 1) {
                Thread.sleep(2000);
            }
        }

        return calcularEstatisticas(tempos, resultado);
    }

    /**
     * Executa o Desafio 5 múltiplas vezes e coleta estatísticas
     */
    private EstatisticasExecucao executarTesteDesafio5(AnaliseForenseAvancada instancia, String caminhoArquivo, Trabalho trabalho) throws Exception {
        List<Long> tempos = new ArrayList<>();
        Object resultado = null;

        for (int i = 0; i < NUMERO_EXECUCOES; i++) {
            LocalDateTime inicio = LocalDateTime.now();
            long tempoInicio = System.nanoTime();
            
            resultado = instancia.rastrearContaminacao(caminhoArquivo, "eve", "bob");
            
            long tempoFim = System.nanoTime();
            LocalDateTime fim = LocalDateTime.now();
            double tempoMs = (tempoFim - tempoInicio) / 1_000_000.0;
            tempos.add(tempoFim - tempoInicio);
            
            // Salvar registro individual no banco
            TestesTrabalho testeIndividual = new TestesTrabalho();
            testeIndividual.setTrabalho(trabalho);
            testeIndividual.setHorarioInicio(inicio);
            testeIndividual.setHorarioFim(fim);
            testeIndividual.setStatusExecucao("COMPLETED");
            testeIndividual.setCategoria("EFICIENCIA_DESAFIO_5");
            testeIndividual.setQuantidadeTestesUnitarios(i + 1);
            testeIndividual.setPontuacao(tempoMs);
            testeIndividual.setResultado(String.format("Execução %d - Tempo: %.2f ms", i + 1, tempoMs));
            testesTrabalhoRepository.save(testeIndividual);
            
            // Sleep de 2 segundos entre execuções
            if (i < NUMERO_EXECUCOES - 1) {
                Thread.sleep(2000);
            }
        }

        return calcularEstatisticas(tempos, resultado);
    }

    /**
     * Calcula estatísticas de desempenho a partir dos tempos coletados
     */
    private EstatisticasExecucao calcularEstatisticas(List<Long> temposNano, Object resultado) {
        // Converter nanosegundos para milissegundos
        List<Double> temposMs = temposNano.stream()
                .map(t -> t / 1_000_000.0)
                .sorted()
                .toList();

        double tempoMedio = temposMs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double tempoMinimo = temposMs.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double tempoMaximo = temposMs.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        // Calcular desvio padrão
        double variancia = temposMs.stream()
                .mapToDouble(t -> Math.pow(t - tempoMedio, 2))
                .average()
                .orElse(0.0);
        double desvioPadrao = Math.sqrt(variancia);

        // Calcular mediana
        double mediana;
        int tamanho = temposMs.size();
        if (tamanho % 2 == 0) {
            mediana = (temposMs.get(tamanho / 2 - 1) + temposMs.get(tamanho / 2)) / 2.0;
        } else {
            mediana = temposMs.get(tamanho / 2);
        }

        // Calcular percentis
        double percentil95 = temposMs.get((int) (tamanho * 0.95));
        double percentil99 = temposMs.get((int) (tamanho * 0.99));

        return new EstatisticasExecucao(
                tempoMedio, tempoMinimo, tempoMaximo, desvioPadrao,
                mediana, percentil95, percentil99, resultado
        );
    }

    /**
     * Calcula a pontuação baseada no tempo de execução vs threshold
     * Fórmula: Pontos = 8 × (1 - tempo/threshold) se tempo < threshold, senão 0
     */
    private double calcularPontuacao(double tempoMedio, double threshold) {
        if (tempoMedio >= threshold) {
            return 0.0;
        }
        double pontos = PONTOS_POR_DESAFIO * (1.0 - (tempoMedio / threshold));
        return Math.max(0.0, pontos); // Garantir que não seja negativo
    }

    /**
     * Formata as estatísticas para exibição
     */
    private String formatarEstatisticas(EstatisticasExecucao stats, double threshold, double pontos) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Tempo Médio: %.2f ms\n", stats.getTempoMedio()));
        sb.append(String.format("Tempo Mínimo: %.2f ms\n", stats.getTempoMinimo()));
        sb.append(String.format("Tempo Máximo: %.2f ms\n", stats.getTempoMaximo()));
        sb.append(String.format("Mediana: %.2f ms\n", stats.getMediana()));
        sb.append(String.format("Desvio Padrão: %.2f ms\n", stats.getDesvioPadrao()));
        sb.append(String.format("Percentil 95%%: %.2f ms\n", stats.getPercentil95()));
        sb.append(String.format("Percentil 99%%: %.2f ms\n", stats.getPercentil99()));
        sb.append(String.format("Threshold: %.2f ms\n", threshold));
        
        if (stats.getTempoMedio() < threshold) {
            sb.append(String.format("✓ Performance APROVADA (%.1f%% do threshold)\n", 
                    (stats.getTempoMedio() / threshold) * 100));
        } else {
            sb.append(String.format("✗ Performance REPROVADA (%.1f%% acima do threshold)\n", 
                    ((stats.getTempoMedio() - threshold) / threshold) * 100));
        }
        
        sb.append(String.format("Pontuação: %.2f/%.0f pontos\n", pontos, PONTOS_POR_DESAFIO));
        
        return sb.toString();
    }

    /**
     * Classe interna para armazenar estatísticas de execução
     */
    private static class EstatisticasExecucao {
        private final double tempoMedio;
        private final double tempoMinimo;
        private final double tempoMaximo;
        private final double desvioPadrao;
        private final double mediana;
        private final double percentil95;
        private final double percentil99;
        private final Object resultado;

        public EstatisticasExecucao(double tempoMedio, double tempoMinimo, double tempoMaximo,
                                     double desvioPadrao, double mediana, double percentil95,
                                     double percentil99, Object resultado) {
            this.tempoMedio = tempoMedio;
            this.tempoMinimo = tempoMinimo;
            this.tempoMaximo = tempoMaximo;
            this.desvioPadrao = desvioPadrao;
            this.mediana = mediana;
            this.percentil95 = percentil95;
            this.percentil99 = percentil99;
            this.resultado = resultado;
        }

        public double getTempoMedio() { return tempoMedio; }
        public double getTempoMinimo() { return tempoMinimo; }
        public double getTempoMaximo() { return tempoMaximo; }
        public double getDesvioPadrao() { return desvioPadrao; }
        public double getMediana() { return mediana; }
        public double getPercentil95() { return percentil95; }
        public double getPercentil99() { return percentil99; }
        public Object getResultado() { return resultado; }
    }
}
