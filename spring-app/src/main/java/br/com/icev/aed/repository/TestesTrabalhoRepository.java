package br.com.icev.aed.repository;

import br.com.icev.aed.entity.TestesTrabalho;
import br.com.icev.aed.entity.Trabalho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestesTrabalhoRepository extends JpaRepository<TestesTrabalho, Long> {
    
    // Busca todos os testes de um trabalho específico
    List<TestesTrabalho> findByTrabalho(Trabalho trabalho);
    
    // Busca todos os testes de um trabalho por ID
    List<TestesTrabalho> findByTrabalhoId(Long trabalhoId);
    
    // Busca testes por status
    List<TestesTrabalho> findByStatusExecucao(String statusExecucao);
    
    // Busca testes por categoria
    List<TestesTrabalho> findByCategoria(String categoria);
    
    // Busca testes de um trabalho específico por categoria
    List<TestesTrabalho> findByTrabalhoAndCategoria(Trabalho trabalho, String categoria);
    
    // Busca o último teste de um trabalho
    TestesTrabalho findFirstByTrabalhoOrderByHorarioInicioDesc(Trabalho trabalho);
}
