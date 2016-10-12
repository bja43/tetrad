///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;

import static com.sun.tools.doclint.Entity.phi;

/**
 * This class provides the data structures and methods for carrying out the Cyclic Causal Discovery algorithm (CCD)
 * described by Thomas Richardson and Peter Spirtes in Chapter 7 of Computation, Causation, & Discovery by Glymour and
 * Cooper eds.  The comments that appear below are keyed to the algorithm specification on pp. 269-271. </p> The search
 * method returns an instance of a Graph but it also constructs two lists of node triples which represent the underlines
 * and dotted underlines that the algorithm discovers.
 *
 * @author Frank C. Wimberly
 * @author Joseph Ramsey
 */
public final class CcdMax implements GraphSearch {
    private IndependenceTest independenceTest;
    private int depth = -1;
    private IKnowledge knowledge;
    private List<Node> nodes;
    private boolean applyR1 = false;

    public CcdMax(IndependenceTest test) {
        if (test == null) throw new NullPointerException();
        this.independenceTest = test;
        this.nodes = test.getVariables();
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * The search method assumes that the IndependenceTest provided to the constructor is a conditional independence
     * oracle for the SEM (or Bayes network) which describes the causal structure of the population. The method returns
     * a PAG instantiated as a Tetrad GaSearchGraph which represents the equivalence class of digraphs which are
     * d-separation equivalent to the digraph of the underlying model (SEM or BN). </p> Although they are not returned
     * by the search method it also computes two lists of triples which, respectively store the underlines and dotted
     * underlines of the PAG.
     */
    public Graph search() {
        Map<Triple, Set<Node>> supSepsets = new HashMap<>();

        // Step A
        Graph psi = stepA();

        SepsetsMinScore sepsets = new SepsetsMinScore(psi, independenceTest, -1);

        stepB(psi, sepsets);
//        stepC(psi, sepsets);
        stepD(psi, sepsets, supSepsets);
        stepE(psi, supSepsets);
        stepF(psi, sepsets, supSepsets);

        return psi;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return 0;
    }

    public boolean isApplyR1() {
        return applyR1;
    }

    public void setApplyR1(boolean applyR1) {
        this.applyR1 = applyR1;
    }

    //======================================== PRIVATE METHODS ====================================//

    private Graph stepA() {
        FasStableConcurrent fas = new FasStableConcurrent(null, independenceTest);
        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        fas.setRecordSepsets(false);
        Graph psi = fas.search();
        psi.reorientAllWith(Endpoint.CIRCLE);
        return psi;
    }

    private void stepB(Graph psi, SepsetProducer sepsets) {
        final Map<Triple, Double> colliders = new ConcurrentHashMap<>();
        final Map<Triple, Double> noncolliders = new ConcurrentHashMap<>();

        List<Node> nodes = psi.getNodes();

        class Task extends RecursiveTask<Boolean> {
            private SepsetProducer sepsets;
            private final Map<Triple, Double> colliders;
            private final Map<Triple, Double> noncolliders;
            private int from;
            private int to;
            private int chunk = 100;
            private List<Node> nodes;
            private Graph psi;

            public Task(SepsetProducer sepsets, List<Node> nodes, Graph graph,
                        Map<Triple, Double> colliders,
                        Map<Triple, Double> noncolliders, int from, int to) {
                this.sepsets = sepsets;
                this.nodes = nodes;
                this.psi = graph;
                this.from = from;
                this.to = to;
                this.colliders = colliders;
                this.noncolliders = noncolliders;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        doNodeCollider(sepsets, psi, colliders, noncolliders, nodes.get(i));
                    }

                    return true;
                } else {
                    int mid = (to + from) / 2;

                    Task left = new Task(sepsets, nodes, psi, colliders, noncolliders, from, mid);
                    Task right = new Task(sepsets, nodes, psi, colliders, noncolliders, mid, to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        Task task = new Task(sepsets, nodes, psi, colliders, noncolliders, 0, nodes.size());

        ForkJoinPoolInstance.getInstance().getPool().invoke(task);

        List<Triple> collidersList = new ArrayList<>(colliders.keySet());
        List<Triple> noncollidersList = new ArrayList<>(noncolliders.keySet());

        Collections.sort(collidersList, new Comparator<Triple>() {

            @Override
            public int compare(Triple o1, Triple o2) {
                return -Double.compare(colliders.get(o2), colliders.get(o1));
            }
        });

        for (Triple triple : collidersList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            if (!(psi.getEndpoint(b, a) == Endpoint.ARROW || psi.getEndpoint(b, c) == Endpoint.ARROW)) {
                psi.removeEdge(a, b);
                psi.removeEdge(c, b);
                psi.addDirectedEdge(a, b);
                psi.addDirectedEdge(c, b);
            }
        }

        for (Triple triple : noncollidersList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            psi.addUnderlineTriple(a, b, c);
        }

        orientAwayFromArrow(psi);
    }

    private void doNodeCollider(SepsetProducer sepsets, Graph psi, Map<Triple, Double> colliders,
                                Map<Triple, Double> noncolliders, Node b) {
        List<Node> adjacentNodes = psi.getAdjacentNodes(b);

        if (adjacentNodes.size() < 2) {
            return;
        }

        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            Node a = adjacentNodes.get(combination[0]);
            Node c = adjacentNodes.get(combination[1]);

            // Skip triples that are shielded.
            if (psi.isAdjacentTo(a, c)) {
                continue;
            }

            List<Node> S = sepsets.getSepset(a, c);
            double score = sepsets.getScore();

            if (S == null) continue;

            if (S.contains(b)) {
                noncolliders.put(new Triple(a, b, c), score);
            } else {
                colliders.put(new Triple(a, b, c), score);
            }
        }
    }

    private void stepC(Graph psi, SepsetsMinScore sepsets) {
        TetradLogger.getInstance().log("info", "\nStep C");

        for (Edge edge : psi.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            // Orientable...
            if (!(psi.getEndpoint(y, x) == Endpoint.CIRCLE &&
                    (psi.getEndpoint(x, y) == Endpoint.CIRCLE || psi.getEndpoint(x, y) == Endpoint.TAIL))) {
                continue;
            }

            if (wouldCreateBadCollider(x, y, psi)) {
                continue;
            }

            List<Node> adjx = psi.getAdjacentNodes(x);
            List<Node> adjy = psi.getAdjacentNodes(y);

            Set<Node> possibleA = new HashSet<>();
            possibleA.addAll(adjx);
            possibleA.addAll(adjy);
            addAdjacents(possibleA, psi);
            addAdjacents(possibleA, psi);
            possibleA.remove(x);
            possibleA.remove(y);
            possibleA.removeAll(adjx);
            possibleA.removeAll(adjy);

            // Check each A
            for (Node a : possibleA) {
                List<Node> sepset = sepsets.getSepset(a, y);

                if (sepset.contains(x)) continue;

                if (sepsets.isIndependent(a, x, sepset)) {
                    continue;
                }

                psi.removeEdge(x, y);
                psi.addDirectedEdge(y, x);
                orientAwayFromArrow(y, x, psi);
                System.out.println("Orienting step C " + psi.getEdge(x, y));
                break;
            }
        }
    }

    private void stepD(Graph psi, SepsetProducer sepsets, final Map<Triple, Set<Node>> supSepsets) {
        Map<Node, List<Node>> local = new HashMap<>();

        for (Node node : psi.getNodes()) {
            local.put(node, local(psi, node));
        }

        class Task extends RecursiveTask<Boolean> {
            private Graph psi;
            private SepsetProducer sepsets;
            private Map<Triple, Set<Node>> supSepsets;
            private Map<Node, List<Node>> local;
            private int from;
            private int to;
            private int chunk = 20;

            public Task(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets,
                        Map<Node, List<Node>> local, int from, int to) {
                this.psi = psi;
                this.sepsets = sepsets;
                this.supSepsets = supSepsets;
                this.local = local;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        for (int j = 0; j < nodes.size(); j++) {
                            Node a = nodes.get(i);
                            Node c = nodes.get(j);
                            if (psi.isAdjacentTo(a, c)) continue;
                            doNodeStepD(psi, sepsets, supSepsets, local, a, c);
                        }
                    }

                    return true;
                } else {
                    int mid = (to + from) / 2;

                    Task left = new Task(psi, sepsets, supSepsets, local, from, mid);
                    Task right = new Task(psi, sepsets, supSepsets, local, mid, to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        Task task = new Task(psi, sepsets, supSepsets, local, 0, nodes.size());

        ForkJoinPoolInstance.getInstance().getPool().invoke(task);
    }

    private void doNodeStepD(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets,
                             Map<Node, List<Node>> local, Node a, Node c) {
        if (a == c) return;
        if (psi.isAdjacentTo(a, c)) return;

        List<Node> adj = psi.getAdjacentNodes(a);
        adj.retainAll(psi.getAdjacentNodes(c));

        for (Node b : adj) {
            if (!psi.isDefCollider(a, b, c)) {
                continue;
            }

            List<Node> S = sepsets.getSepset(a, c);
            if (S == null) continue;
            Set<Node> TT_S = new HashSet<>(local.get(a));
            TT_S.removeAll(S);
            TT_S.remove(b);
            TT_S.remove(c);
            List<Node> TT = new ArrayList<>(TT_S);
            System.out.println(" a = " + a + " b = " + b + " c = " + c +
                    "S = " + S + " local(a) = " + local.get(a) + " TT = " + TT);

            DepthChoiceGenerator gen2 = new DepthChoiceGenerator(TT.size(), -1);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                Set<Node> T = GraphUtils.asSet(choice2, TT);
                Set<Node> supsepset = new HashSet<>(T);
                supsepset.addAll(S);
                supsepset.add(b);

                System.out.println("supsepset(" + a + ", " + c + ") = " + supsepset);

                if (sepsets.isIndependent(a, c, new ArrayList<>(supsepset))) {
                    psi.addDottedUnderlineTriple(a, b, c);
                    supSepsets.put(new Triple(a, b, c), supsepset);
                }
            }
        }
    }

    private void stepE(Graph psi, Map<Triple, Set<Node>> supSepset) {
        TetradLogger.getInstance().log("info", "\nStep E");

        for (Triple triple : psi.getDottedUnderlines()) {
            System.out.println(triple);

            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            List<Node> bAdj = psi.getAdjacentNodes(b);

            for (Node d : bAdj) {
                if (d == a || d == c) continue;
                if (!psi.isDefCollider(a, d, c)) continue;

                if (supSepset.get(triple).contains(d)) {

                    // Orient B*-oD as B*-D
                    psi.setEndpoint(b, d, Endpoint.TAIL);
                } else {
                    if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                        continue;
                    }

                    if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                        continue;
                    }

                    if (wouldCreateBadCollider(b, d, psi)) {
                        continue;
                    }

                    // Or orient Bo-oD or B-oD as B->D...
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                    orientAwayFromArrow(b, d, psi);
                }
            }
        }
    }

    private void stepF(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets) {
        for (Triple triple : psi.getDottedUnderlines()) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            Set<Node> adj = new HashSet<>(psi.getAdjacentNodes(a));
            adj.addAll(psi.getAdjacentNodes(c));

            for (Node d : adj) {
                if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                    continue;
                }

                if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                    continue;
                }

                if (wouldCreateBadCollider(b, d, psi)) {
                    continue;
                }

                //...and D is not adjacent to both A and C in psi...
                if (psi.isAdjacentTo(a, d) && psi.isAdjacentTo(c, d)) {
                    continue;
                }

                //...and B and D are adjacent...
                if (!psi.isAdjacentTo(b, d)) {
                    continue;
                }

                Set<Node> supSepUnionD = new HashSet<>();
                supSepUnionD.add(d);
                supSepUnionD.addAll(supSepsets.get(triple));
                List<Node> listSupSepUnionD = new ArrayList<>(supSepUnionD);

                //If A and C are a pair of vertices d-connected given
                //SupSepset<A,B,C> union {D} then orient Bo-oD or B-oD
                //as B->D in psi.
                if (!sepsets.isIndependent(a, c, listSupSepUnionD)) {
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                    orientAwayFromArrow(b, d, psi);
                }
            }
        }
    }

    private List<Node> local(Graph psi, Node x) {
        Set<Node> nodes = new HashSet<>(psi.getAdjacentNodes(x));

        for (Node y : new HashSet<>(nodes)) {
            for (Node z : psi.getAdjacentNodes(y)) {
                if (psi.isDefCollider(x, y, z)) {
                    if (z != x) {
                        nodes.add(z);
                    }
                }
            }
        }

        return new ArrayList<>(nodes);
    }

    private void orientAwayFromArrow(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();

            edge = graph.getEdge(n1, n2);

            if (edge.pointsTowards(n1)) {
                orientAwayFromArrow(n2, n1, graph);
            } else if (edge.pointsTowards(n2)) {
                orientAwayFromArrow(n1, n2, graph);
            }
        }
    }

    private void orientAwayFromArrow(Node a, Node b, Graph graph) {
        if (!isApplyR1()) return;

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            orientAwayFromArrowVisit(a, b, c, graph);
        }
    }

    private boolean orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {
        if (!Edges.isNondirectedEdge(graph.getEdge(b, c))) {
            return false;
        }

        if (!(graph.isUnderlineTriple(a, b, c))) {
            return false;
        }


        if (graph.getEdge(b, c).pointsTowards(b)) {
            return false;
        }

        graph.removeEdge(b, c);
        graph.addDirectedEdge(b, c);

        for (Node d : graph.getAdjacentNodes(c)) {
            if (d == b) return true;

            Edge bc = graph.getEdge(b, c);

            if (!orientAwayFromArrowVisit(b, c, d, graph)) {
                graph.removeEdge(b, c);
                graph.addEdge(bc);
            }
        }

        return true;
    }

    private void addAdjacents(Set<Node> possibleA, Graph graph) {
        for (Node node : new HashSet<>(possibleA)) {
            possibleA.addAll(graph.getAdjacentNodes(node));
        }
    }

    private boolean wouldCreateBadCollider(Node x, Node y, Graph psi) {
        for (Node z : psi.getAdjacentNodes(y)) {
            if (x == z) continue;
            if (psi.getEndpoint(x, y) != Endpoint.ARROW && psi.getEndpoint(z, y) == Endpoint.ARROW) return true;
        }

        return false;
    }
}





