package org.linqs.psl.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.apache.commons.cli.CommandLine;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.*;
import org.linqs.psl.parser.CommandLineLoader;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.*;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MyGroundRule implements WeightedGroundRule {
    private double weight;

    public MyGroundRule(double weight) {
        this.weight = weight;
    }

    @Override
    public Set<GroundAtom> getAtoms() {
        return null;
    }

    @Override
    public List<GroundRule> negate() {
        return null;
    }

    @Override
    public String baseToString() {
        return null;
    }

    @Override
    public WeightedRule getRule() {
        return null;
    }

    @Override
    public boolean isSquared() {
        return false;
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public void setWeight(double weight) {
        this.weight = weight;
    }

    @Override
    public GeneralFunction getFunctionDefinition() {
        return null;
    }

    @Override
    public double getIncompatibility() {
        return 0;
    }

    @Override
    public double getIncompatibility(GroundAtom replacementAtom, float replacementValue) {
        return 0;
    }
}

public class CustomADMMLauncher {
    private static final Logger log = LoggerFactory.getLogger(CustomADMMLauncher.class);

    public static void main(String[] args) {
        try {
            CommandLineLoader commandLineLoader = new CommandLineLoader(args);
            CommandLine givenOptions = commandLineLoader.getParsedOptions();
            if (givenOptions.getOptionValue(CommandLineLoader.OPTION_POTENTIALS_JSON_FILE_PATH) == null) {
                return;
            };

            // "examples/hl_mrfPotentials_N1000.json" should return 0
            // "examples/simple_squared_example.json" should return 1.875 (with y1 = w2/(w1 + w2) = 0.375)
            // "examples/first_example.json" should return 0.207 (first example):

            String path = givenOptions.getOptionValue(CommandLineLoader.OPTION_POTENTIALS_JSON_FILE_PATH);
            Path p = Paths.get(path);
            JSONParser jsonParser = new JSONParser(p);

            ArrayList<MyGroundRule> myGroundRules = new ArrayList<>();
            ArrayList<ADMMObjectiveTerm> terms = new ArrayList<>();
            ADMMTermStore termStore = new ADMMTermStore();

            for (JSONParser.Potential potential : jsonParser.getPotentials()) {
                MyGroundRule myGroundRule = new MyGroundRule(potential.getWeight());

                ArrayList<LocalVariable> lvs = new ArrayList<>();
                ArrayList<Float> coeffs = new ArrayList<>();

                for (String posVar : potential.getPositiveVariables()) {
                    LocalVariable lv = termStore.createLocalVariable(posVar);
                    lvs.add(lv);
                    coeffs.add(1.0f);
                }

                for (String negVar : potential.getNegativeVariables()) {
                    LocalVariable lv = termStore.createLocalVariable(negVar);
                    lvs.add(lv);
                    coeffs.add(-1.0f);
                }

                float constant = potential.getConstant();
                int size = lvs.size();
                float[] coeffsArray = new float[lvs.size()];
                for (int i = 0; i < size; ++i) {
                    coeffsArray[i] = coeffs.get(i);
                }

                Hyperplane<LocalVariable> hp = new Hyperplane<>(lvs.toArray(LocalVariable[]::new),
                        coeffsArray,
                        -constant,
                        size);

                ADMMObjectiveTerm term;
                if (potential.isSquared()) {
                    term = new SquaredHingeLossTerm(myGroundRule, hp);
                } else {
                    term = new HingeLossTerm(myGroundRule, hp);
                }

                myGroundRules.add(myGroundRule);
                terms.add(term);
            }

            for (int i = 0; i < myGroundRules.size(); ++i) {
                termStore.add(myGroundRules.get(i), terms.get(i));
            }

            ADMMReasoner admmReasoner = new ADMMReasoner();
            admmReasoner.optimize(termStore);

            for (Map.Entry<String, Integer> entry : termStore.variableStringIndexes.entrySet()) {
                System.out.println(entry.getKey() + ": " + termStore.variableValues.get(entry.getValue()));
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}
