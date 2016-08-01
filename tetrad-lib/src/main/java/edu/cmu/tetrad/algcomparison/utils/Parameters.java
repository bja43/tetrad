package edu.cmu.tetrad.algcomparison.utils;

import java.util.*;

/**
 * Stores a list of named parameters with their values. Stores default values for known
 * parameters. Returns a list of parameters with their values, for the parameters whose
 * values have been retrieved, using the toString method.
 *
 * @author jdramsey
 */
public class Parameters {
    private Map<String, Object[]> parameters = new LinkedHashMap<>();
    private Set<String> usedParameters = new LinkedHashSet<>();
    private Map<String, Object> overriddenParameters = new HashMap<>();

    public Parameters() {

        // Defaults
        put("numMeasures", 10);
        put("numLatents", 0);
        put("avgDegree", 2);
        put("maxDegree", 100);
        put("maxIndegree", 100);
        put("maxOutdegree", 100);
        put("connected", 0);
        put("sampleSize", 1000);
        put("numRuns", 1);
        put("alpha", 0.001);
        put("penaltyDiscount", 4);
        put("fgsDepth", -1);
        put("depth", -1);
        put("coefLow", 0.5);
        put("coefHigh", 1.5);
        put("variance", -1);
        put("varianceLow", 1.0);
        put("varianceHigh", 3.0);
        put("printWinners", 0);
        put("printAverages", 0);
        put("printAverageTables", 1);
        put("printGraph", 0);
        put("percentDiscrete", 50);
        put("ofInterestCutoff", 0.05);
        put("printGraphs", 0);
        put("numCategories", 4);
        put("samplePrior", 1);
        put("structurePrior", 1);
        put("mgmParam1", 0.1);
        put("mgmParam2", 0.1);
        put("mgmParam3", 0.1);
        put("scaleFreeAlpha", 0.9);
        put("scaleFreeBeta", 0.05);
        put("scaleFreeDeltaIn", 3);
        put("scaleFreeDeltaOut", 3);
        put("generalSemFunctionTemplateMeasured", "TSUM(NEW(B)*$)");
        put("generalSemFunctionTemplateLatent", "TSUM(NEW(B)*$)");
        put("generalSemErrorTemplate", "Beta(2, 5)");
        put("varLow", 1);
        put("varHigh", 3);
    }

    public Parameters(Parameters parameters) {
        this.parameters = new LinkedHashMap<>(parameters.parameters);
        this.usedParameters = new LinkedHashSet<>(parameters.usedParameters);
        this.overriddenParameters = new HashMap<>(parameters.overriddenParameters);
    }

    /**
     * Returns a list of the parameters whoese values were actually used in the course of
     * the simulatoin.
     *
     * @return This list, in String form.
     */
    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (String param : usedParameters) {
            builder.append("\n").append(param).append(" = ").append(parameters.get(param)[0]);
        }

        return builder.toString();
    }

    /**
     * Returns the integer values of the given parameter.
     *
     * @param name The name of the parameter.
     * @return The integer value of this parameter.
     */
    public int getInt(String name) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return ((Number) o).intValue();
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }

        usedParameters.add(name);
        Object o = parameters.get(name)[0];
        return ((Number) o).intValue();
    }

    /**
     * Returns the double values of the given parameter.
     *
     * @param name The name of the parameter.
     * @return The double value of this parameter.
     */
    public double getDouble(String name) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return ((Number) o).doubleValue();
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }

        usedParameters.add(name);
        Object o = parameters.get(name)[0];

        if (!(o instanceof Number)) {
            throw new IllegalArgumentException("Not a Number parameter: " + name);
        }

        return ((Number) o).doubleValue();
    }

    /**
     * Returns the string values of the given parameter.
     *
     * @param name The name of the parameter.
     * @return The double value of this parameter.
     */
    public String getString(String name) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return (String) o;
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }
        usedParameters.add(name);
        Object o = parameters.get(name)[0];
        return (String) o;
    }


    /**
     * Sets the value(s) of the given parameter to a list of strings.
     *
     * @param name The name of the parameter.
     * @param n    A list of values for the parameter.
     */
    public void put(String name, Object... n) {
        parameters.put(name, n);
    }

    /**
     * Sets the value(s) of the given parameter to a list of values.
     *
     * @param name The name of the parameter.
     * @param s    A list of strings for the parameter.
     */
    public void put(String name, String... s) {
        parameters.put(name, s);
    }

    /**
     * Returns the number of values for the parameter.
     *
     * @param parameter The parameter of the parameter.
     * @return The number of values set for that parameter.
     */
    public int getNumValues(String parameter) {
        Object[] objects = parameters.get(parameter);
        if (objects == null) {
            throw new IllegalArgumentException("Expecting a value for parameter '" + parameter + "'");
        }
        return objects.length;
    }

    /**
     * Returns the values set for the given parameter. Usually of length 1.
     *
     * @param parameter The name of the parameter.
     * @return The array of values.
     */
    public Object[] getValues(String parameter) {
        return parameters.get(parameter);
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param parameter The name of the parameter.
     * @param value     The value of the parameter (a single value).
     */
    public void setValue(String parameter, Object value) {
        parameters.put(parameter, new Object[]{value});
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param parameter The name of the parameter.
     * @param value     The value of the parameter (a single value).
     */
    public void setValue(String parameter, String value) {
        parameters.put(parameter, new String[]{value});
    }

    /**
     * Sets a map of parameters to override the current ones.
     *
     * @param parameters A map from parameter names to values.
     */
    public void setOverriddenParameters(Map<String, Object> parameters) {
        this.overriddenParameters = parameters;
    }

    public Map<String, Object[]> getParameters() {
        return parameters;
    }

    public Set<String> getUsedParameters() {
        return usedParameters;
    }
}
