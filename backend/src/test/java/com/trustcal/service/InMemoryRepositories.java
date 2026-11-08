package com.trustcal.service;

import com.trustcal.entity.*;
import com.trustcal.repository.*;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tiny in-memory implementations of the Spring Data repositories (built with dynamic proxies),
 * so the services can be tested quickly without a database or a Spring context.
 * Only the repository methods the services actually call are supported. Thread-safe, because
 * evaluation runs save from several threads.
 */
class InMemoryRepositories {

    private final Map<Class<?>, List<Object>> tables = new HashMap<>();
    private long sequence = 1;

    final DomainProfileRepository domains = create(DomainProfileRepository.class, DomainProfile.class);
    final DecisionTaskRepository tasks = create(DecisionTaskRepository.class, DecisionTask.class);
    final ParticipantRepository participants = create(ParticipantRepository.class, Participant.class);
    final TrialRepository trials = create(TrialRepository.class, Trial.class);
    final PostSurveyRepository surveys = create(PostSurveyRepository.class, PostSurvey.class);
    final StudySettingRepository settings = create(StudySettingRepository.class, StudySetting.class);
    final EvaluationRunRepository runs = create(EvaluationRunRepository.class, EvaluationRun.class);
    final LlmAdviceRepository advices = create(LlmAdviceRepository.class, LlmAdvice.class);

    @SuppressWarnings("unchecked")
    private <R> R create(Class<R> repositoryInterface, Class<?> entity) {
        List<Object> rows = tables.computeIfAbsent(entity, k -> new ArrayList<>());
        return (R) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{repositoryInterface},
                (proxy, method, args) -> {
                    synchronized (rows) {
                        return handle(repositoryInterface, rows, proxy, method, args);
                    }
                });
    }

    private Object handle(Class<?> repositoryInterface, List<Object> rows, Object proxy, Method method, Object[] args) {
        String name = method.getName();
        switch (name) {
            case "save": {
                Object o = args[0];
                assignId(o);
                Object id = idOf(o);
                // replace an existing row with the same id (e.g. a setting saved twice)
                rows.removeIf(r -> r != o && Objects.equals(idOf(r), id));
                if (!rows.contains(o)) rows.add(o);
                return o;
            }
            case "saveAll": {
                List<Object> saved = new ArrayList<>();
                for (Object o : (Iterable<?>) args[0]) {
                    assignId(o);
                    rows.add(o);
                    saved.add(o);
                }
                return saved;
            }
            case "findAll":
            case "findAllWithParticipant":
            case "findAllByOrderByCreatedAtDesc":
            case "findAllWithTask":
                return new ArrayList<>(rows);
            case "findAllCompletedWithDetails":
                return rows.stream().filter(o -> ((Trial) o).isCompleted()).collect(Collectors.toList());
            case "count":
                return (long) rows.size();
            case "findById":
                return rows.stream().filter(o -> Objects.equals(args[0], idOf(o))).findFirst();
            case "findByCode":
                return rows.stream().filter(o -> args[0].equals(property(o, "getCode"))).findFirst();
            case "findAllByOrderByDisplayOrderAsc":
                return rows.stream()
                        .sorted(Comparator.comparing(o -> (Integer) property(o, "getDisplayOrder")))
                        .collect(Collectors.toList());
            case "findByPublicId":
                return rows.stream().filter(o -> args[0].equals(((Participant) o).getPublicId())).findFirst();
            case "countByConditionAndSimulatedAndDemo":
                return rows.stream().map(o -> (Participant) o)
                        .filter(p -> p.getCondition() == args[0] && p.isSimulated() == (Boolean) args[1]
                                && p.isDemo() == (Boolean) args[2])
                        .count();
            case "findBySimulatedFalseAndDemoFalseOrderByCreatedAtDesc":
                return rows.stream().filter(o -> ((Participant) o).isStudyData()).collect(Collectors.toList());
            case "findBySimulatedTrue":
                return rows.stream().filter(o -> ((Participant) o).isSimulated()).collect(Collectors.toList());
            case "findByDemoTrue":
                return rows.stream().filter(o -> ((Participant) o).isDemo()).collect(Collectors.toList());
            case "findByParticipantAndTrialIndex":
                return rows.stream().map(o -> (Trial) o)
                        .filter(t -> t.getParticipant() == args[0] && t.getTrialIndex().equals(args[1]))
                        .findFirst();
            case "findByParticipantOrderByTrialIndexAsc":
                return rows.stream().map(o -> (Trial) o)
                        .filter(t -> t.getParticipant() == args[0])
                        .sorted(Comparator.comparing(Trial::getTrialIndex))
                        .collect(Collectors.toList());
            case "countByParticipantAndCompletedTrue":
                return rows.stream().map(o -> (Trial) o)
                        .filter(t -> t.getParticipant() == args[0] && t.isCompleted()).count();
            case "existsByParticipant":
                return rows.stream().anyMatch(o -> ((PostSurvey) o).getParticipant() == args[0]);
            case "findByParticipant":
                return rows.stream().filter(o -> ((PostSurvey) o).getParticipant() == args[0]).findFirst();
            case "deleteByParticipantIn": {
                List<?> ps = (List<?>) args[0];
                rows.removeIf(o -> ps.contains(property(o, "getParticipant")));
                return null;
            }
            case "deleteAll":
                for (Object o : (Iterable<?>) args[0]) rows.remove(o);
                return null;
            // ---- evaluation runs
            case "findAllByOrderByIdDesc":
                return rows.stream().sorted(Comparator.comparing(o -> -((EvaluationRun) o).getId()))
                        .collect(Collectors.toList());
            case "findByStatus":
                return rows.stream().filter(o -> ((EvaluationRun) o).getStatus() == args[0]).collect(Collectors.toList());
            // ---- LLM calls
            case "findByRunIdWithTask":
                return rows.stream().map(o -> (LlmAdvice) o).filter(a -> Objects.equals(a.getRunId(), args[0]))
                        .sorted(Comparator.comparing(LlmAdvice::getId)).collect(Collectors.toList());
            case "countByRunId":
                return rows.stream().filter(o -> Objects.equals(((LlmAdvice) o).getRunId(), args[0])).count();
            case "findFrozenWithTask":
                return rows.stream().filter(o -> ((LlmAdvice) o).isFrozen()).collect(Collectors.toList());
            case "findFirstByTaskAndFrozenTrue":
                return rows.stream().map(o -> (LlmAdvice) o)
                        .filter(a -> a.isFrozen() && a.getTask() == args[0]).findFirst();
            case "toString":
                return repositoryInterface.getSimpleName();
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                throw new UnsupportedOperationException("Not supported in tests: " + name);
        }
    }

    private synchronized void assignId(Object entity) {
        try {
            Method getId = entity.getClass().getMethod("getId");
            if (getId.invoke(entity) == null) {
                entity.getClass().getMethod("setId", Long.class).invoke(entity, sequence++);
            }
        } catch (NoSuchMethodException e) {
            // entity with a natural key (StudySetting)
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object idOf(Object o) {
        for (String getter : new String[]{"getId", "getKey"}) {
            try {
                return o.getClass().getMethod(getter).invoke(o);
            } catch (NoSuchMethodException ignored) {
                // try the next one
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private static Object property(Object o, String getter) {
        try {
            return o.getClass().getMethod(getter).invoke(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
