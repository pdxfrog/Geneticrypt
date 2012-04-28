package com.nomachetejuggling.geneticrypt.simulators.genetic;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.nomachetejuggling.geneticrypt.genes.Candidate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.google.common.collect.Collections2.transform;

public class ThreadedGeneticSimulator<T extends Candidate> extends GeneticSimulator<T> {
    private int populationSize;

    public ThreadedGeneticSimulator(int populationSize) {
        this.populationSize = populationSize;
    }

    protected int getPopulationSize() {
        return populationSize;
    }

    @Override
    protected T findBest(List<T> population) {
        try {
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            List<Future<Result<T>>> futures = executor.invokeAll(scorersFor(population));
            executor.shutdown();

            return scoreOrdering().max(resultsOf(futures)).getGene();

        } catch (InterruptedException e) {
            throw new RuntimeException("Could not invoke threads", e);
        }
    }

    private Collection<Callable<Result<T>>> scorersFor(List<T> population) {
        return transform(population, new Function<T, Callable<Result<T>>>() {
            @Override
            public Callable<Result<T>> apply(T input) {
                return new Scorer<T>(input);
            }
        });

    }

    private Ordering<Result<T>> scoreOrdering() {
        return Ordering.from(new Comparator<Result<T>>() {
            @Override
            public int compare(Result<T> resultFuture, Result<T> resultFuture1) {
                return resultFuture.getScore().compareTo(resultFuture1.getScore());

            }
        });
    }

    private Iterable<Result<T>> resultsOf(List<Future<Result<T>>> results) {
        return Iterables.transform(results, new Function<Future<Result<T>>, Result<T>>() {
            @Override
            public Result<T> apply(Future<Result<T>> input) {
                try {
                    return input.get();
                } catch (Exception e) {
                    throw new RuntimeException("Problem while computing result", e);
                }
            }
        });
    }

    @Override
    protected List<T> newPopulationFrom(T bestFit) {
        List<T> population = new ArrayList<T>();
        //population.add(bestFit); //Include the previous generation's best fit as well
        for (int i = 0; i < populationSize; i++) {
            population.add((T) bestFit.mutate());
        }
        return population;
    }

    @Override
    protected List<T> createInitialPopulation(Supplier<T> supplier) {
        List<T> population = new ArrayList<T>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            population.add(supplier.get());
        }
        return population;
    }

    private class Scorer<T extends Candidate> implements Callable<Result<T>> {
        private T gene;

        Scorer(T gene) {
            this.gene = gene;
        }

        @Override
        public Result<T> call() throws Exception {
            return new Result<T>(gene, gene.score());
        }
    }

    private class Result<T> {
        private T gene;
        private Double score;

        public Result(T gene, Double score) {
            this.gene = gene;
            this.score = score;
        }

        public T getGene() {
            return gene;
        }

        public Double getScore() {
            return score;
        }
    }

}