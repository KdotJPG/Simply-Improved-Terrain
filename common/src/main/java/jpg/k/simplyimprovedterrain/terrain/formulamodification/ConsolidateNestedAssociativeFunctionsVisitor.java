package jpg.k.simplyimprovedterrain.terrain.formulamodification;

import jpg.k.simplyimprovedterrain.terrain.customdensityfunctions.MultiArgumentSimpleFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

// TODO account for MulOrAdd
enum ConsolidateNestedAssociativeFunctionsVisitor implements DensityFunction.Visitor {
    INSTANCE;

    private ConsolidateNestedAssociativeFunctionsVisitor() { }

    public DensityFunction apply(DensityFunction function) {

        // Only focus on TwoArgumentSimpleFunction, which can be add/mul/min/max
        if (!(function instanceof DensityFunctions.TwoArgumentSimpleFunction twoArgumentSimpleFunction)) return function;

        Stream<DensityFunction> argument1EntryStream = getArgumentsOrNull(twoArgumentSimpleFunction.argument1(), twoArgumentSimpleFunction.type());
        Stream<DensityFunction> argument2EntryStream = getArgumentsOrNull(twoArgumentSimpleFunction.argument2(), twoArgumentSimpleFunction.type());

        if (argument1EntryStream != null && argument2EntryStream != null) {
            return MultiArgumentSimpleFunction.createSimplfied(
                    twoArgumentSimpleFunction.type(),
                    Stream.concat(argument1EntryStream, argument2EntryStream).collect(getConstantConsolidatedListCollector(twoArgumentSimpleFunction.type()))
            );
        } else if (argument1EntryStream != null) {
            return MultiArgumentSimpleFunction.createSimplfied(
                    twoArgumentSimpleFunction.type(),
                    Stream.concat(
                            argument1EntryStream,
                            Stream.of(twoArgumentSimpleFunction.argument2())
                    ).collect(getConstantConsolidatedListCollector(twoArgumentSimpleFunction.type()))
            );
        } else if (argument2EntryStream != null) {
            return MultiArgumentSimpleFunction.createSimplfied(
                    twoArgumentSimpleFunction.type(),
                    Stream.concat(
                            Stream.of(twoArgumentSimpleFunction.argument2()),
                            argument2EntryStream
                    ).collect(getConstantConsolidatedListCollector(twoArgumentSimpleFunction.type()))
            );
        }

        return function;
    }

    private static Stream<DensityFunction> getArgumentsOrNull(DensityFunction function,
                                                              DensityFunctions.TwoArgumentSimpleFunction.Type type) {

        DensityFunction actualFunction = CourseAlteringNode.unwrap(function);

        if (actualFunction instanceof DensityFunctions.TwoArgumentSimpleFunction twoArgumentSimpleFunction &&
                twoArgumentSimpleFunction.type() == type) {
            return Stream.of(twoArgumentSimpleFunction.argument1(), twoArgumentSimpleFunction.argument2());
        }


        if (actualFunction instanceof MultiArgumentSimpleFunction multiArgumentSimpleFunction &&
                multiArgumentSimpleFunction.type() == type) {
            return multiArgumentSimpleFunction.arguments().stream();
        }

        return null;
    }

    private static Collector<DensityFunction, ConsolidateNestedAssociativeFunctionsVisitor.ArgumentConstantConsolidator, List<DensityFunction>> getConstantConsolidatedListCollector(
            DensityFunctions.TwoArgumentSimpleFunction.Type type
    ) {
        return switch (type) {
            case ADD -> CONSTANT_CONSOLIDATED_LIST_COLLECTOR_ADD;
            case MUL -> CONSTANT_CONSOLIDATED_LIST_COLLECTOR_MUL;
            case MAX -> CONSTANT_CONSOLIDATED_LIST_COLLECTOR_MAX;
            case MIN -> CONSTANT_CONSOLIDATED_LIST_COLLECTOR_MIN;
        };
    }

    private static final Collector<DensityFunction, ConsolidateNestedAssociativeFunctionsVisitor.ArgumentConstantConsolidator, List<DensityFunction>>
            CONSTANT_CONSOLIDATED_LIST_COLLECTOR_ADD = createConstantConsolidatedListCollector(DensityFunctions.TwoArgumentSimpleFunction.Type.ADD);
    private static final Collector<DensityFunction, ConsolidateNestedAssociativeFunctionsVisitor.ArgumentConstantConsolidator, List<DensityFunction>>
            CONSTANT_CONSOLIDATED_LIST_COLLECTOR_MUL = createConstantConsolidatedListCollector(DensityFunctions.TwoArgumentSimpleFunction.Type.MUL);
    private static final Collector<DensityFunction, ConsolidateNestedAssociativeFunctionsVisitor.ArgumentConstantConsolidator, List<DensityFunction>>
            CONSTANT_CONSOLIDATED_LIST_COLLECTOR_MAX = createConstantConsolidatedListCollector(DensityFunctions.TwoArgumentSimpleFunction.Type.MAX);
    private static final Collector<DensityFunction, ConsolidateNestedAssociativeFunctionsVisitor.ArgumentConstantConsolidator, List<DensityFunction>>
            CONSTANT_CONSOLIDATED_LIST_COLLECTOR_MIN = createConstantConsolidatedListCollector(DensityFunctions.TwoArgumentSimpleFunction.Type.MIN);

    private static Collector<DensityFunction, ConsolidateNestedAssociativeFunctionsVisitor.ArgumentConstantConsolidator, List<DensityFunction>> createConstantConsolidatedListCollector(
            DensityFunctions.TwoArgumentSimpleFunction.Type type
    ) {
        return new Collector<DensityFunction, ArgumentConstantConsolidator, List<DensityFunction>>() {
            @Override
            public Supplier<ArgumentConstantConsolidator> supplier() {
                return () -> new ArgumentConstantConsolidator(type);
            }

            @Override
            public BiConsumer<ArgumentConstantConsolidator, DensityFunction> accumulator() {
                return ArgumentConstantConsolidator::add;
            }

            @Override
            public BinaryOperator<ArgumentConstantConsolidator> combiner() {
                return (a, b) -> { a.addAll(b.list()); return a; };
            }

            @Override
            public Function<ArgumentConstantConsolidator, List<DensityFunction>> finisher() {
                return ArgumentConstantConsolidator::list;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.emptySet();
            }
        };
    }

    private static class ArgumentConstantConsolidator {
        public final DensityFunctions.TwoArgumentSimpleFunction.Type type;
        public final List<DensityFunction> list;
        private int constantIndex;
        private double constantValueAtIndex;

        public ArgumentConstantConsolidator(DensityFunctions.TwoArgumentSimpleFunction.Type type) {
            this.type = type;
            this.list = new ArrayList<>();
            this.constantIndex = -1;
        }

        public List<DensityFunction> list() {
            return this.list;
        }

        public void add(DensityFunction function) {
            DensityFunction actualFunction = CourseAlteringNode.unwrap(function);
            if (actualFunction instanceof DensityFunctions.Constant constant) {
                if (constantIndex >= 0) {
                    constantValueAtIndex = MultiArgumentSimpleFunction.compute(type, constantValueAtIndex, constant.value());
                    list.set(constantIndex, DensityFunctions.constant(constantValueAtIndex));
                    return;
                }

                constantIndex = list.size();
                constantValueAtIndex = constant.value();
            }

            list.add(function);
        }

        public void addAll(Collection<DensityFunction> collection) {
            for (DensityFunction function : collection) {
                add(function);
            }
        }
    }

}
