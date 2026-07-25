package com.storylens.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class PipelineExecutionAspect {

    private static final Logger logger = LoggerFactory.getLogger(PipelineExecutionAspect.class);
    private static final String MDC_REQUEST_ID = "reqId";

    @Pointcut("execution(* com.storylens.plot.StructureGenerationController.generate(..))")
    private void controllerEntryPoint() {
    }

    @Pointcut("execution(* com.storylens.tag.TagDiagnosisService.diagnose(..))")
    private void tagDiagnosisStep() {
    }

    @Pointcut("execution(* com.storylens.cardselection.CardSelectionService.select(..))")
    private void cardSelectionStep() {
    }

    @Pointcut("execution(* com.storylens.plot.StructureGenerationService.generate(..))")
    private void structureGenerationStep() {
    }

    @Pointcut("execution(* com.storylens.verify.ActorEvaluatorService.evaluate(..))")
    private void actorEvaluatorStep() {
    }

    @Pointcut("tagDiagnosisStep() || cardSelectionStep() || structureGenerationStep() || actorEvaluatorStep()")
    private void pipelineStep() {
    }

    @Around("controllerEntryPoint()")
    public Object assignRequestId(ProceedingJoinPoint joinPoint) throws Throwable {
        MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString().substring(0, 8));
        long start = System.currentTimeMillis();
        logger.info("요청 시작: {}", joinPoint.getSignature().getName());
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            logger.warn("요청 실패 ({}ms): {}", System.currentTimeMillis() - start, throwable.getMessage());
            throw throwable;
        } finally {
            logger.info("요청 종료 ({}ms)", System.currentTimeMillis() - start);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    @Around("pipelineStep()")
    public Object logStep(ProceedingJoinPoint joinPoint) throws Throwable {
        String stepName = stepNameOf(joinPoint);
        long start = System.currentTimeMillis();
        logger.info("[{}] 진입", stepName);
        try {
            Object result = joinPoint.proceed();
            logger.info("[{}] 완료 ({}ms)", stepName, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable throwable) {
            logger.warn("[{}] 실패 ({}ms): {}", stepName, System.currentTimeMillis() - start, throwable.getMessage());
            throw throwable;
        }
    }

    private String stepNameOf(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        return switch (className) {
            case "TagDiagnosisService" -> "태그진단";
            case "CardSelectionService" -> "카드선정";
            case "StructureGenerationService" -> "구조생성";
            case "ActorEvaluatorService" -> "액터평가";
            default -> className;
        };
    }
}
