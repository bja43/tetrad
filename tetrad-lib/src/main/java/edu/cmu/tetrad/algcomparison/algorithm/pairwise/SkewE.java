package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs2;

import java.util.ArrayList;
import java.util.List;

/**
 * SkewE.
 *
 * @author jdramsey
 */
public class SkewE implements Algorithm, TakesInitialGraph {
    private Algorithm initialGraph = null;

    public SkewE(Algorithm initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        Graph initial = null;

        if (initialGraph != null) {
            initial = initialGraph.search(dataSet, parameters);
        }

        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);

        Lofs2 lofs = new Lofs2(initial, dataSets);
        lofs.setRule(Lofs2.Rule.SkewE);

        return lofs.orient();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return graph;
    }

    @Override
    public String getDescription() {
        return "SkewE" + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        return initialGraph.getParameters();
    }
}
