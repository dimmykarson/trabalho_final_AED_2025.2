package br.com.icev.aed.service;

import br.com.icev.aed.entity.TestesTrabalho;
import br.com.icev.aed.entity.Trabalho;
import br.com.icev.aed.repository.TestesTrabalhoRepository;
import br.com.icev.aed.repository.TrabalhoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CalculoScoreService {

    private static final Logger logger = LoggerFactory.getLogger(CalculoScoreService.class);

    @Autowired
    private TrabalhoRepository trabalhoRepository;

    @Autowired
    private TestesTrabalhoRepository testesTrabalhoRepository;

    // Constantes de pontuação
    private static final double PONTOS_BASE_TODOS = 10.0;
    private static final double PONTOS_MAX_EFICIENCIA = 40.0;
    private static final double PONTOS_MAX_CORRETUDE = 70.0;
    private static final double LIMIAR_VENCEDOR = 95.0;
    private static final double LIMIAR_MINIMO_CORRETUDE = 40.0;
    private static final double NOTA_MAXIMA = 100.0;
    private static final double NOTA_MAXIMA_SEM_VENCEDOR = 95.0;

    /**
     * Calcula o score final de todos os trabalhos
     */
    public Map<String, Object> calcularScoreFinal() {
        logger.info("Iniciando cálculo de score final para todos os trabalhos");

        List<Trabalho> trabalhos = trabalhoRepository.findAll();
        
        if (trabalhos.isEmpty()) {
            logger.warn("Nenhum trabalho encontrado para calcular score");
            return Map.of("erro", "Nenhum trabalho encontrado");
        }

        // 1. Calcular dados de cada trabalho
        List<DadosTrabalho> dadosTrabalhos = new ArrayList<>();
        
        for (Trabalho trabalho : trabalhos) {
            try {
                DadosTrabalho dados = calcularDadosTrabalho(trabalho);
                dadosTrabalhos.add(dados);
            } catch (Exception e) {
                logger.error("Erro ao calcular dados do trabalho {}", trabalho.getId(), e);
            }
        }

        // 2. Identificar o melhor tempo médio (Tf)
        double tf = dadosTrabalhos.stream()
                .mapToDouble(DadosTrabalho::getSomaMelhorTempoDesafios)
                .min()
                .orElse(Double.MAX_VALUE);

        logger.info("Melhor tempo (Tf) identificado: {} ms", tf);

        // 3. Calcular pontuação de eficiência para cada trabalho
        for (DadosTrabalho dados : dadosTrabalhos) {
            double tm = dados.getSomaMelhorTempoDesafios();
            
            if (tm == tf) {
                dados.setPontosEficiencia(PONTOS_MAX_EFICIENCIA);
            } else {
                double pontosEficiencia = PONTOS_MAX_EFICIENCIA * (tf / tm);
                dados.setPontosEficiencia(pontosEficiencia);
            }
            
            // Calcular pontuação base
            double pontuacaoBase = dados.getPontosCorretude() + dados.getPontosEficiencia() + PONTOS_BASE_TODOS;
            dados.setPontuacaoBase(pontuacaoBase);
        }

        // 4. Identificar vencedor (se houver)
        Optional<DadosTrabalho> vencedorOpt = dadosTrabalhos.stream()
                .filter(d -> d.getPontuacaoBase() >= LIMIAR_VENCEDOR)
                .max(Comparator.comparingDouble(DadosTrabalho::getPontuacaoBase));

        DadosTrabalho vencedor = vencedorOpt.orElse(null);
        
        if (vencedor != null) {
            logger.info("Vencedor identificado: Trabalho ID {} com {} pontos", 
                    vencedor.getTrabalho().getId(), vencedor.getPontuacaoBase());
        } else {
            logger.info("Nenhum trabalho atingiu o limiar de {} pontos para ser vencedor", LIMIAR_VENCEDOR);
        }

        // 5. Calcular nota final de cada trabalho
        for (DadosTrabalho dados : dadosTrabalhos) {
            double notaFinal = calcularNotaFinal(dados, vencedor);
            dados.setNotaFinal(notaFinal);
            
            // Atualizar no banco de dados
            Trabalho trabalho = dados.getTrabalho();
            trabalho.setResultadoTestes(gerarRelatorioDetalhado(dados, vencedor));
            trabalho.setDataTeste(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date()));
            trabalho.setTestado(true);
            trabalhoRepository.save(trabalho);
        }

        // 6. Preparar resposta
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("totalTrabalhos", dadosTrabalhos.size());
        resultado.put("tempoMaisRapido_Tf_ms", tf);
        resultado.put("vencedor", vencedor != null ? 
                Map.of(
                    "trabalhoId", vencedor.getTrabalho().getId(),
                    "alunos", getAlunosString(vencedor.getTrabalho()),
                    "pontuacaoBase", vencedor.getPontuacaoBase(),
                    "notaFinal", vencedor.getNotaFinal()
                ) : null);
        
        List<Map<String, Object>> rankings = dadosTrabalhos.stream()
                .sorted(Comparator.comparingDouble(DadosTrabalho::getNotaFinal).reversed())
                .map(d -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("trabalhoId", d.getTrabalho().getId());
                    map.put("alunos", getAlunosString(d.getTrabalho()));
                    map.put("pontosCorretude", d.getPontosCorretude());
                    map.put("pontosEficiencia", d.getPontosEficiencia());
                    map.put("pontosBase", PONTOS_BASE_TODOS);
                    map.put("pontuacaoBase", d.getPontuacaoBase());
                    map.put("notaFinal", d.getNotaFinal());
                    map.put("tempoTotal_ms", d.getSomaMelhorTempoDesafios());
                    return map;
                })
                .collect(Collectors.toList());
        
        resultado.put("ranking", rankings);

        logger.info("Cálculo de score final concluído");
        return resultado;
    }

    /**
     * Calcula os dados de um trabalho específico
     */
    private DadosTrabalho calcularDadosTrabalho(Trabalho trabalho) {
        DadosTrabalho dados = new DadosTrabalho();
        dados.setTrabalho(trabalho);

        // Buscar testes unitários (corretude)
        List<TestesTrabalho> testesUnitarios = testesTrabalhoRepository
                .findByTrabalhoAndCategoria(trabalho, "TESTE_UNITARIO");
        
        double pontosCorretude = testesUnitarios.stream()
                .mapToDouble(t -> t.getPontuacao() != null ? t.getPontuacao() : 0.0)
                .sum();
        
        dados.setPontosCorretude(Math.min(pontosCorretude, PONTOS_MAX_CORRETUDE));

        // Buscar melhor tempo de cada desafio de eficiência
        double[] melhoresTempos = new double[5];
        
        for (int i = 1; i <= 5; i++) {
            String categoria = "EFICIENCIA_DESAFIO_" + i;
            List<TestesTrabalho> testesDesafio = testesTrabalhoRepository
                    .findByTrabalhoAndCategoria(trabalho, categoria);
            
            double melhorTempo = testesDesafio.stream()
                    .mapToDouble(t -> t.getPontuacao() != null ? t.getPontuacao() : Double.MAX_VALUE)
                    .min()
                    .orElse(Double.MAX_VALUE);
            
            melhoresTempos[i - 1] = melhorTempo;
        }
        
        dados.setMelhoresTemposDesafios(melhoresTempos);
        
        // Calcular soma dos melhores tempos (Tm)
        double somaTm = Arrays.stream(melhoresTempos)
                .filter(t -> t != Double.MAX_VALUE)
                .sum();
        
        dados.setSomaMelhorTempoDesafios(somaTm);

        return dados;
    }

    /**
     * Calcula a nota final de um trabalho
     */
    private double calcularNotaFinal(DadosTrabalho dados, DadosTrabalho vencedor) {
        double pontosCorretude = dados.getPontosCorretude();
        double pontuacaoBase = dados.getPontuacaoBase();

        // Regra: se corretude < 40, nota final = pontos de corretude
        if (pontosCorretude < LIMIAR_MINIMO_CORRETUDE) {
            logger.info("Trabalho {} com corretude {} < 40. Nota final = corretude", 
                    dados.getTrabalho().getId(), pontosCorretude);
            return pontosCorretude;
        }

        // Se houver vencedor
        if (vencedor != null) {
            if (dados == vencedor) {
                return NOTA_MAXIMA;
            }
            
            // Nota proporcional ao vencedor
            double notaFinal = 99.0 * (pontuacaoBase / vencedor.getPontuacaoBase());
            return Math.round(notaFinal * 100.0) / 100.0;
        }

        // Se não houver vencedor, limitar a 95
        return Math.min(NOTA_MAXIMA_SEM_VENCEDOR, pontuacaoBase);
    }

    /**
     * Gera relatório detalhado do trabalho
     */
    private String gerarRelatorioDetalhado(DadosTrabalho dados, DadosTrabalho vencedor) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RELATÓRIO FINAL DE PONTUAÇÃO ===\n\n");
        
        Trabalho trabalho = dados.getTrabalho();
        sb.append(String.format("Trabalho ID: %d\n", trabalho.getId()));
        sb.append(String.format("Alunos: %s\n\n", getAlunosString(trabalho)));
        
        sb.append("--- PONTOS DE CORRETUDE (Testes Unitários) ---\n");
        sb.append(String.format("Pontos obtidos: %.2f / %.0f\n\n", 
                dados.getPontosCorretude(), PONTOS_MAX_CORRETUDE));
        
        sb.append("--- PONTOS DE EFICIÊNCIA ---\n");
        sb.append("Melhores tempos por desafio:\n");
        double[] tempos = dados.getMelhoresTemposDesafios();
        for (int i = 0; i < tempos.length; i++) {
            sb.append(String.format("  Desafio %d: %.2f ms\n", i + 1, 
                    tempos[i] == Double.MAX_VALUE ? 0.0 : tempos[i]));
        }
        sb.append(String.format("Tempo total (Tm): %.2f ms\n", dados.getSomaMelhorTempoDesafios()));
        sb.append(String.format("Pontos de eficiência: %.2f / %.0f\n\n", 
                dados.getPontosEficiencia(), PONTOS_MAX_EFICIENCIA));
        
        sb.append("--- PONTUAÇÃO BASE ---\n");
        sb.append(String.format("Pontos de corretude: %.2f\n", dados.getPontosCorretude()));
        sb.append(String.format("Pontos de eficiência: %.2f\n", dados.getPontosEficiencia()));
        sb.append(String.format("Pontos base (todos): %.0f\n", PONTOS_BASE_TODOS));
        sb.append(String.format("TOTAL BASE: %.2f pontos\n\n", dados.getPontuacaoBase()));
        
        sb.append("--- NOTA FINAL ---\n");
        
        if (dados.getPontosCorretude() < LIMIAR_MINIMO_CORRETUDE) {
            sb.append(String.format("⚠️ ATENÇÃO: Corretude (%.2f) < %.0f pontos\n", 
                    dados.getPontosCorretude(), LIMIAR_MINIMO_CORRETUDE));
            sb.append("Nota final = Pontos de corretude\n");
        } else if (vencedor != null && dados == vencedor) {
            sb.append("🏆 VENCEDOR! Pontuação base >= 95 pontos\n");
            sb.append("Nota final: 100 pontos\n");
        } else if (vencedor != null) {
            sb.append(String.format("Cálculo: 99 × (%.2f / %.2f) = %.2f\n", 
                    dados.getPontuacaoBase(), vencedor.getPontuacaoBase(), dados.getNotaFinal()));
        } else {
            sb.append("Nenhum vencedor identificado (ninguém >= 95 pontos)\n");
            sb.append(String.format("Nota final = min(95, %.2f) = %.2f\n", 
                    dados.getPontuacaoBase(), dados.getNotaFinal()));
        }
        
        sb.append(String.format("\n📊 NOTA FINAL: %.2f pontos\n", dados.getNotaFinal()));
        
        return sb.toString();
    }

    /**
     * Obtém string formatada com matrículas dos alunos
     */
    private String getAlunosString(Trabalho trabalho) {
        StringBuilder sb = new StringBuilder(trabalho.getMatriculaAluno1());
        if (trabalho.getMatriculaAluno2() != null) {
            sb.append(", ").append(trabalho.getMatriculaAluno2());
        }
        if (trabalho.getMatriculaAluno3() != null) {
            sb.append(", ").append(trabalho.getMatriculaAluno3());
        }
        return sb.toString();
    }

    /**
     * Classe interna para armazenar dados calculados de um trabalho
     */
    private static class DadosTrabalho {
        private Trabalho trabalho;
        private double pontosCorretude;
        private double pontosEficiencia;
        private double pontuacaoBase;
        private double notaFinal;
        private double[] melhoresTemposDesafios = new double[5];
        private double somaMelhorTempoDesafios;

        // Getters e Setters
        public Trabalho getTrabalho() { return trabalho; }
        public void setTrabalho(Trabalho trabalho) { this.trabalho = trabalho; }
        
        public double getPontosCorretude() { return pontosCorretude; }
        public void setPontosCorretude(double pontosCorretude) { this.pontosCorretude = pontosCorretude; }
        
        public double getPontosEficiencia() { return pontosEficiencia; }
        public void setPontosEficiencia(double pontosEficiencia) { this.pontosEficiencia = pontosEficiencia; }
        
        public double getPontuacaoBase() { return pontuacaoBase; }
        public void setPontuacaoBase(double pontuacaoBase) { this.pontuacaoBase = pontuacaoBase; }
        
        public double getNotaFinal() { return notaFinal; }
        public void setNotaFinal(double notaFinal) { this.notaFinal = notaFinal; }
        
        public double[] getMelhoresTemposDesafios() { return melhoresTemposDesafios; }
        public void setMelhoresTemposDesafios(double[] melhoresTemposDesafios) { 
            this.melhoresTemposDesafios = melhoresTemposDesafios; 
        }
        
        public double getSomaMelhorTempoDesafios() { return somaMelhorTempoDesafios; }
        public void setSomaMelhorTempoDesafios(double somaMelhorTempoDesafios) { 
            this.somaMelhorTempoDesafios = somaMelhorTempoDesafios; 
        }
    }
}
