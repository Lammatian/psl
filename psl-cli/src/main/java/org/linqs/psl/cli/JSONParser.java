package org.linqs.psl.cli;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JSONParser {
    private static final String NAME_KEY = "potential";
    private static final String WEIGHT_KEY = "rule_weight";
    private static final String CONST_KEY = "constant";
    private static final String VAR_KEY = "variables";
    private static final String POS_VAR_KEY = "positive";
    private static final String NEG_VAR_KEY = "negative";
    private static final String SQUARED_KEY = "squared";

    public static class Potential {
        private final String name;
        private final float weight;
        private final float constant;
        private final boolean isSquared;
        private final List<String> posVars;
        private final List<String> negVars;

        public Potential(JSONObject jsonPotential) {
            name = jsonPotential.getString(NAME_KEY);
            weight = jsonPotential.getFloat(WEIGHT_KEY);
            constant = jsonPotential.getFloat(CONST_KEY);
            isSquared = jsonPotential.getBoolean(SQUARED_KEY);

            JSONObject variables = jsonPotential.getJSONObject(VAR_KEY);
            posVars = variables.getJSONArray(POS_VAR_KEY)
                    .toList()
                    .stream()
                    .map(i -> (String)i)
                    .collect(Collectors.toList());
            negVars = variables.getJSONArray(NEG_VAR_KEY)
                    .toList()
                    .stream()
                    .map(i -> (String)i)
                    .collect(Collectors.toList());
        }

        public String getName() {
            return name;
        }

        public float getWeight() {
            return weight;
        }

        public float getConstant() {
            return constant;
        }

        public boolean isSquared() {
            return isSquared;
        }

        public List<String> getPositiveVariables() {
            return posVars;
        }

        public List<String> getNegativeVariables() {
            return negVars;
        }

        public List<String> getAllVariables() {
            return Stream.of(posVars, negVars).flatMap(Collection::stream).collect(Collectors.toList());
        }
    }

    private JSONArray jsonPotentials;
    private final ArrayList<Potential> potentials;

    public JSONParser(Path path) {
        try {
            String jsonString = Files.readString(path, StandardCharsets.US_ASCII);
            jsonPotentials = new JSONArray(jsonString);
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        potentials = new ArrayList<>();
        for (Object jsonPotential : jsonPotentials) {
            if (jsonPotential instanceof JSONObject) {
                potentials.add(new Potential((JSONObject) jsonPotential));
            }
        }
    }

    public ArrayList<Potential> getPotentials() {
        return potentials;
    }
}
