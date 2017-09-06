package org.elasticsearch.index.engine;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@SuppressWarnings("unused") //invoked by benchmarking framework
public class InternalEngineBenchmark {


    @Setup
    public void setUp() throws Exception {
        // TODO: Add any benchmark setup code here
    }

    // You can use this idiom to store thread local data. JMH will create one instance per thread and inject it to your benchmark method.
    @State(Scope.Thread)
    static class Ids {
        private final String prefix = UUID.randomUUID().toString();

        private int counter;

        public String nextId() {
            counter++;
            return prefix + counter;
        }
    }

    public Object indexDoc(Ids ids) throws Exception {
        //TODO: Index a document into Lucene here - return some result to avoid dead code elimination
        return null;
    }

    // These are three example benchmark methods. They will index documents with 1, 2 and 4 threads

    @Benchmark
    @Group("index_1")
    @GroupThreads()
    public Object indexDoc_1(Ids ids) throws Exception {
        return indexDoc(ids);
    }

    @Benchmark
    @Group("index_2")
    @GroupThreads(2)
    public Object indexDoc_2(Ids ids) throws Exception {
        return indexDoc(ids);
    }

    @Benchmark
    @Group("index_4")
    @GroupThreads(4)
    public Object indexDoc_4(Ids ids) throws Exception {
        return indexDoc(ids);
    }
}
