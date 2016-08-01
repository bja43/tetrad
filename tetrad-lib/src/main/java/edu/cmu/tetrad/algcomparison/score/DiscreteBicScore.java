package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.Score;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class DiscreteBicScore implements ScoreWrapper {

    @Override
    public Score getScore(DataSet dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.BicScore score
                = new edu.cmu.tetrad.search.BicScore(dataSet);
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return score;
    }

    @Override
    public String getDescription() {
        return "Discrete BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        return parameters;
    }

}
