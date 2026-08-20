/*
 * Responsabilidade: Reserva recursos limitados para respostas longas de exportação.
 */
package com.unimedlorena.tools.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RelatorioAsyncConfig implements WebMvcConfigurer {

  private final ThreadPoolTaskExecutor relatorioTaskExecutor;
  private final long timeoutMs;

  public RelatorioAsyncConfig(
    @Qualifier("relatorioTaskExecutor") ThreadPoolTaskExecutor relatorioTaskExecutor,
    @Value("${relatorios.exportacao.async-timeout-ms:7200000}") long timeoutMs
  ) {
    this.relatorioTaskExecutor = relatorioTaskExecutor;
    this.timeoutMs = Math.max(1, timeoutMs);
  }

  @Bean(name = "relatorioTaskExecutor", destroyMethod = "shutdown")
  public static ThreadPoolTaskExecutor relatorioTaskExecutor(
    @Value("${relatorios.exportacao.async.core-pool-size:2}") int corePoolSize,
    @Value("${relatorios.exportacao.async.max-pool-size:4}") int maxPoolSize,
    @Value("${relatorios.exportacao.async.queue-capacity:20}") int queueCapacity
  ) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    int nucleos = Math.max(1, corePoolSize);
    executor.setCorePoolSize(nucleos);
    executor.setMaxPoolSize(Math.max(nucleos, maxPoolSize));
    executor.setQueueCapacity(Math.max(0, queueCapacity));
    executor.setThreadNamePrefix("relatorio-exportacao-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
  }

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    // O timeout cobre toda a geração; CSV e TXT continuam liberando cada página
    // ao cliente para manter a conexão ativa e reduzir o uso de memória.
    configurer.setTaskExecutor(relatorioTaskExecutor);
    configurer.setDefaultTimeout(timeoutMs);
  }
}
