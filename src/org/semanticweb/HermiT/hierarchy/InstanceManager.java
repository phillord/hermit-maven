/* Copyright 2008, 2009, 2010 by the Oxford University Computing Laboratory

   This file is part of HermiT.

   HermiT is free software: you can redistribute it and/or modify
   it under the terms of the GNU Lesser General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   HermiT is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public License
   along with HermiT.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.semanticweb.HermiT.hierarchy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.semanticweb.HermiT.Prefixes;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.HermiT.graph.Graph;
import org.semanticweb.HermiT.hierarchy.DeterministicClassification.GraphNode;
import org.semanticweb.HermiT.hierarchy.RoleElementManager.RoleElement;
import org.semanticweb.HermiT.model.Atom;
import org.semanticweb.HermiT.model.AtomicConcept;
import org.semanticweb.HermiT.model.AtomicRole;
import org.semanticweb.HermiT.model.DLClause;
import org.semanticweb.HermiT.model.DLOntology;
import org.semanticweb.HermiT.model.DLPredicate;
import org.semanticweb.HermiT.model.Individual;
import org.semanticweb.HermiT.model.Inequality;
import org.semanticweb.HermiT.model.InverseRole;
import org.semanticweb.HermiT.model.Role;
import org.semanticweb.HermiT.tableau.ExtensionManager;
import org.semanticweb.HermiT.tableau.ExtensionTable;
import org.semanticweb.HermiT.tableau.InterruptFlag;
import org.semanticweb.HermiT.tableau.Node;
import org.semanticweb.HermiT.tableau.NodeType;
import org.semanticweb.HermiT.tableau.ReasoningTaskDescription;
import org.semanticweb.HermiT.tableau.Tableau;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.reasoner.ReasonerProgressMonitor;

public class InstanceManager {
    public static final int thresholdForAdditionalAxioms=10000;
    
    protected final InterruptFlag m_interruptFlag;
    protected final Reasoner m_reasoner;
    protected final Tableau m_tableau;
    protected final Individual[] m_individuals;
    protected final HashSet<AtomicRole> m_complexRoles;
    protected final Map<AtomicConcept,AtomicConceptElement> m_conceptToElement;
    protected final AtomicConcept m_topConcept;
    protected final AtomicConcept m_bottomConcept;
    protected Hierarchy<AtomicConcept> m_currentConceptHierarchy;
    protected final RoleElementManager m_roleElementManager;
    protected final RoleElement m_topRoleElement;
    protected final RoleElement m_bottomRoleElement;
    protected Hierarchy<RoleElement> m_currentRoleHierarchy;
    protected final boolean m_usesInverseRoles;
    protected final Map<Individual, Node> m_nodesForIndividuals;
    protected final Map<Node,Individual> m_individualsForNodes;
    protected boolean m_isInconsistent;
    protected boolean m_realizationCompleted;
    protected boolean m_roleRealizationCompleted;
    protected boolean m_usesClassifiedConceptHierarchy;
    protected boolean m_usesClassifiedObjectRoleHierarchy;
    protected boolean m_classesInitialised;
    protected boolean m_propertiesInitialised;
    protected boolean m_readingOffFoundPossibleConceptInstance;
    protected boolean m_readingOffFoundPossiblePropertyInstance;
    protected final Map<Individual,Set<Individual>> m_individualToEquivalenceClass;
    protected Map<Set<Individual>,Set<Set<Individual>>> m_individualToPossibleEquivalenceClass;
    protected final ExtensionTable.Retrieval m_binaryRetrieval0Bound;
    protected final ExtensionTable.Retrieval m_binaryRetrieval1Bound;
    protected final ExtensionTable.Retrieval m_binaryRetrieval01Bound;
    protected final ExtensionTable.Retrieval m_ternaryRetrieval1Bound;
    protected final ExtensionTable.Retrieval m_ternaryRetrieval0Bound;
    protected final ExtensionTable.Retrieval m_ternaryRetrieval012Bound;
    protected int m_currentIndividualIndex=0;
    
    public InstanceManager(InterruptFlag interruptFlag, Reasoner reasoner, Tableau tableau, Hierarchy<AtomicConcept> atomicConceptHierarchy, Hierarchy<Role> objectRoleHierarchy) {
        m_interruptFlag=interruptFlag;
        m_interruptFlag.startTask();
        try {
            m_reasoner=reasoner;
            m_tableau=tableau;
            DLOntology dlo=m_reasoner.getDLOntology();
            m_individuals=new ArrayList<Individual>(dlo.getAllIndividuals()).toArray(new Individual[0]);
            m_complexRoles=new HashSet<AtomicRole>();
            m_individualToEquivalenceClass=new HashMap<Individual, Set<Individual>>();
            m_nodesForIndividuals=new HashMap<Individual,Node>();
            for (Individual individual : m_individuals) {
                m_nodesForIndividuals.put(individual,null);
                Set<Individual> equivalentIndividuals=new HashSet<Individual>();
                equivalentIndividuals.add(individual);
                m_individualToEquivalenceClass.put(individual, equivalentIndividuals);
                m_interruptFlag.checkInterrupt();
            }
            m_individualsForNodes=new HashMap<Node,Individual>();
            m_individualToPossibleEquivalenceClass=null;
            
            m_topConcept=AtomicConcept.THING;
            m_bottomConcept=AtomicConcept.NOTHING;
            m_conceptToElement=new HashMap<AtomicConcept, AtomicConceptElement>();
            m_conceptToElement.put(m_topConcept, new AtomicConceptElement(null, null));
            Graph<AtomicConcept> knownConceptSubsumptions=null;
            Set<AtomicConcept> atomicConcepts=null;
            if (atomicConceptHierarchy!=null) {
                m_currentConceptHierarchy=atomicConceptHierarchy;
            } else {
                knownConceptSubsumptions=new Graph<AtomicConcept>();
                atomicConcepts=new HashSet<AtomicConcept>();
                atomicConcepts.add(m_topConcept);
                atomicConcepts.add(m_bottomConcept);
                for (AtomicConcept atomicConcept : dlo.getAllAtomicConcepts()) {
                    if (!Prefixes.isInternalIRI(atomicConcept.getIRI())) {
                        atomicConcepts.add(atomicConcept);
                        addKnownConceptSubsumption(knownConceptSubsumptions,atomicConcept,atomicConcept);
                        addKnownConceptSubsumption(knownConceptSubsumptions,atomicConcept,m_topConcept);
                        addKnownConceptSubsumption(knownConceptSubsumptions,m_bottomConcept,atomicConcept);
                    }
                    m_interruptFlag.checkInterrupt();
                }
                addKnownConceptSubsumption(knownConceptSubsumptions,m_bottomConcept,m_bottomConcept);
            }
            
            m_roleElementManager=new RoleElementManager();
            Graph<Role> knownRoleSubsumptions=null;
            m_topRoleElement=m_roleElementManager.getRoleElement(AtomicRole.TOP_OBJECT_ROLE);
            m_bottomRoleElement=m_roleElementManager.getRoleElement(AtomicRole.BOTTOM_OBJECT_ROLE);
            m_usesInverseRoles=dlo.hasInverseRoles();
            Set<Role> roles=null;
            Set<Role> complexRoles=dlo.getAllComplexObjectRoles();
            if (objectRoleHierarchy!=null) {
                setToClassifiedRoleHierarchy(objectRoleHierarchy);
                for (Role role : complexRoles)
                    if (role instanceof AtomicRole && role!=AtomicRole.TOP_OBJECT_ROLE && role!=AtomicRole.BOTTOM_OBJECT_ROLE)
                        m_complexRoles.add((AtomicRole)role);
            } else {
                knownRoleSubsumptions=new Graph<Role>();
                roles=new HashSet<Role>();
                roles.add(AtomicRole.TOP_OBJECT_ROLE);
                roles.add(AtomicRole.BOTTOM_OBJECT_ROLE);
                roles.addAll(dlo.getAllAtomicObjectRoles());
                for (Role role : roles) {
                    addKnownRoleSubsumption(knownRoleSubsumptions,role,role);
                    addKnownRoleSubsumption(knownRoleSubsumptions,role,AtomicRole.TOP_OBJECT_ROLE);
                    addKnownRoleSubsumption(knownRoleSubsumptions,AtomicRole.BOTTOM_OBJECT_ROLE,role);
                    if (complexRoles.contains(role) && role instanceof AtomicRole && role!=AtomicRole.TOP_OBJECT_ROLE && role!=AtomicRole.BOTTOM_OBJECT_ROLE)
                        m_complexRoles.add((AtomicRole)role);
                    m_interruptFlag.checkInterrupt();
                }
                addKnownRoleSubsumption(knownRoleSubsumptions,AtomicRole.BOTTOM_OBJECT_ROLE,AtomicRole.BOTTOM_OBJECT_ROLE);
            }
            if (atomicConceptHierarchy==null || objectRoleHierarchy==null) {
                updateKnownSubsumptionsUsingToldSubsumers(dlo.getDLClauses(),knownConceptSubsumptions,atomicConcepts,knownRoleSubsumptions,roles);
            }
            if (atomicConceptHierarchy==null)
                m_currentConceptHierarchy=buildTransitivelyReducedConceptHierarchy(knownConceptSubsumptions);
            if (objectRoleHierarchy==null)
                m_currentRoleHierarchy=buildTransitivelyReducedRoleHierarchy(knownRoleSubsumptions);
            ExtensionManager extensionManager=m_tableau.getExtensionManager();
            m_binaryRetrieval0Bound=extensionManager.getBinaryExtensionTable().createRetrieval(new boolean[] { true, false }, ExtensionTable.View.TOTAL);
            m_binaryRetrieval1Bound=extensionManager.getBinaryExtensionTable().createRetrieval(new boolean[] { false, true }, ExtensionTable.View.TOTAL);
            m_binaryRetrieval01Bound=extensionManager.getBinaryExtensionTable().createRetrieval(new boolean[] { true, true }, ExtensionTable.View.TOTAL);
            m_ternaryRetrieval1Bound=extensionManager.getTernaryExtensionTable().createRetrieval(new boolean[] { false,true,false }, ExtensionTable.View.TOTAL);
            m_ternaryRetrieval0Bound=extensionManager.getTernaryExtensionTable().createRetrieval(new boolean[] { true,false,false }, ExtensionTable.View.TOTAL);
            m_ternaryRetrieval012Bound=extensionManager.getTernaryExtensionTable().createRetrieval(new boolean[] { true,true,true }, ExtensionTable.View.TOTAL);
        }
        finally {
            m_interruptFlag.endTask();
        }
    }
    protected void addKnownConceptSubsumption(Graph<AtomicConcept> knownSubsumptions,AtomicConcept subConcept,AtomicConcept superConcept) {
        knownSubsumptions.addEdge(subConcept,superConcept);
    }
    protected void addKnownRoleSubsumption(Graph<Role> knownSubsumptions,Role subRole,Role superRole) {
        knownSubsumptions.addEdge(subRole,superRole);
        if (m_usesInverseRoles)
            knownSubsumptions.addEdge(subRole.getInverse(),superRole.getInverse());
    }
    protected void updateKnownSubsumptionsUsingToldSubsumers(Set<DLClause> dlClauses, Graph<AtomicConcept> knownConceptSubsumptions,Set<AtomicConcept> concepts,Graph<Role> knownRoleSubsumptions,Set<Role> roles) {
        boolean requiresConceptSubsumers=knownConceptSubsumptions!=null;
        boolean requiresRoleSubsumers=knownRoleSubsumptions!=null;
        if (requiresConceptSubsumers || requiresRoleSubsumers) {
            for (DLClause dlClause : dlClauses) {
                if (dlClause.getHeadLength()==1 && dlClause.getBodyLength()==1) {
                    DLPredicate headPredicate=dlClause.getHeadAtom(0).getDLPredicate();
                    DLPredicate bodyPredicate=dlClause.getBodyAtom(0).getDLPredicate();
                    if (requiresConceptSubsumers && headPredicate instanceof AtomicConcept && bodyPredicate instanceof AtomicConcept) {
                        AtomicConcept headConcept=(AtomicConcept)headPredicate;
                        AtomicConcept bodyConcept=(AtomicConcept)bodyPredicate;
                        if (concepts.contains(headConcept) && concepts.contains(bodyConcept))
                            addKnownConceptSubsumption(knownConceptSubsumptions,bodyConcept,headConcept);
                    } else if (requiresRoleSubsumers && headPredicate instanceof AtomicRole && bodyPredicate instanceof AtomicRole) {
                        AtomicRole headRole=(AtomicRole)headPredicate;
                        AtomicRole bodyRole=(AtomicRole)bodyPredicate;
                        if (roles.contains(headRole) && roles.contains(bodyRole)) {
                            if (dlClause.getBodyAtom(0).getArgument(0)!=dlClause.getHeadAtom(0).getArgument(0))
                                // r -> s^- and r^- -> s
                                addKnownRoleSubsumption(knownRoleSubsumptions,InverseRole.create(bodyRole),headRole);
                            else 
                                // r-> s and r^- -> s^-
                                addKnownRoleSubsumption(knownRoleSubsumptions,bodyRole,headRole);
                        }
                    }
                }
                m_interruptFlag.checkInterrupt();
            }
        }
    }
    protected Hierarchy<AtomicConcept> buildTransitivelyReducedConceptHierarchy(Graph<AtomicConcept> knownSubsumptions) {
        final Map<AtomicConcept,GraphNode<AtomicConcept>> allSubsumers=new HashMap<AtomicConcept,GraphNode<AtomicConcept>>();
        for (AtomicConcept element : knownSubsumptions.getElements())
            allSubsumers.put(element,new GraphNode<AtomicConcept>(element,knownSubsumptions.getSuccessors(element)));
        m_interruptFlag.checkInterrupt();
        return DeterministicClassification.buildHierarchy(m_topConcept,m_bottomConcept,allSubsumers);
    }
    public void setToClassifiedConceptHierarchy(Hierarchy<AtomicConcept> atomicConceptHierarchy) {
        m_currentConceptHierarchy=atomicConceptHierarchy;
        if (m_classesInitialised && m_individuals.length>0) {
            for (HierarchyNode<AtomicConcept> node : m_currentConceptHierarchy.getAllNodes()) {
                if (node.m_representative!=m_bottomConcept) {
                    AtomicConcept representativeConcept=node.getRepresentative();
                    Set<Individual> known=new HashSet<Individual>();
                    Set<Individual> possible=null;
                    for (AtomicConcept concept : node.getEquivalentElements()) {
                        if (m_conceptToElement.containsKey(concept)) {
                            AtomicConceptElement element=m_conceptToElement.get(concept);
                            known.addAll(element.m_knownInstances);
                            if (possible==null) {
                                possible=new HashSet<Individual>(element.m_possibleInstances);
                            } else {
                                possible.retainAll(element.m_possibleInstances);
                            }
                            m_conceptToElement.remove(concept);
                        }
                    }
                    if (possible!=null)
                        possible.removeAll(known);
                    if (!known.isEmpty()||possible!=null||representativeConcept==m_topConcept) 
                        m_conceptToElement.put(representativeConcept, new AtomicConceptElement(known, possible));
                }
            }
            // clean up known and possibles
            Queue<HierarchyNode<AtomicConcept>> toProcess=new LinkedList<HierarchyNode<AtomicConcept>>();
            toProcess.addAll(m_currentConceptHierarchy.m_bottomNode.m_parentNodes);
            while (!toProcess.isEmpty()) {
                HierarchyNode<AtomicConcept> current=toProcess.remove();
                AtomicConcept currentConcept=current.getRepresentative();
                AtomicConceptElement currentElement=m_conceptToElement.get(currentConcept);
                if (currentElement!=null) {
                    Set<HierarchyNode<AtomicConcept>> ancestors=current.getAncestorNodes();
                    ancestors.remove(current);
                    for (HierarchyNode<AtomicConcept> ancestor : ancestors) {
                        AtomicConcept ancestorConcept=ancestor.getRepresentative();
                        AtomicConceptElement ancestorElement=m_conceptToElement.get(ancestorConcept);
                        if (ancestorElement!=null) {
                            ancestorElement.m_knownInstances.removeAll(currentElement.m_knownInstances);
                            ancestorElement.m_possibleInstances.removeAll(currentElement.m_knownInstances);
                            ancestorElement.m_possibleInstances.removeAll(currentElement.m_possibleInstances);
                        }
                    }
                    for (HierarchyNode<AtomicConcept> parent : current.getParentNodes())
                        if (!toProcess.contains(parent)) 
                            toProcess.add(parent);
                }
                m_interruptFlag.checkInterrupt();
            }
        }
        m_usesClassifiedConceptHierarchy=true;
    }
    protected Hierarchy<RoleElement> buildTransitivelyReducedRoleHierarchy(Graph<Role> knownSubsumptions) {
        final Map<Role,GraphNode<Role>> allSubsumers=new HashMap<Role,GraphNode<Role>>();
        for (Role role : knownSubsumptions.getElements())
            allSubsumers.put(role,new GraphNode<Role>(role,knownSubsumptions.getSuccessors(role)));
        m_interruptFlag.checkInterrupt();
        return transformRoleHierarchy(DeterministicClassification.buildHierarchy(AtomicRole.TOP_OBJECT_ROLE,AtomicRole.BOTTOM_OBJECT_ROLE,allSubsumers));
    }
    /**
     * Removes the inverses from the given hierarchy and then converts Role hierarchy nodes to RoleElement hierarchy nodes, which can store 
     * known and possible instances. 
     * @param roleHierarchy
     * @return a hierarchy containing role element nodes and no inverses
     */
    public Hierarchy<RoleElement> transformRoleHierarchy(final Hierarchy<Role> roleHierarchy) {
        Hierarchy<Role> newHierarchy=removeInverses(roleHierarchy);
        Hierarchy.Transformer<Role,RoleElement> transformer=new Hierarchy.Transformer<Role,RoleElement>() {
            public RoleElement transform(Role role) {
                m_interruptFlag.checkInterrupt();
                return m_roleElementManager.getRoleElement(role);
            }
            public RoleElement determineRepresentative(Role oldRepresentative,Set<RoleElement> newEquivalentElements) {
                RoleElement representative=transform(oldRepresentative);
                for (RoleElement newEquiv : newEquivalentElements) {
                    if (!newEquiv.equals(representative)) {
                        for (Individual individual : newEquiv.m_knownRelations.keySet()) {
                            Set<Individual> successors=representative.m_knownRelations.get(individual);
                            if (successors==null) {
                                successors=new HashSet<Individual>();
                                representative.m_knownRelations.put(individual, successors);
                            }
                            successors.addAll(newEquiv.m_knownRelations.get(individual));
                        }
                        for (Individual individual : newEquiv.m_possibleRelations.keySet()) {
                            Set<Individual> successors=representative.m_possibleRelations.get(individual);
                            if (successors!=null) {
                                successors.retainAll(newEquiv.m_possibleRelations.get(individual));
                            }
                        }
                        newEquiv.m_knownRelations.clear();
                        newEquiv.m_possibleRelations.clear();
                    }
                }
                m_interruptFlag.checkInterrupt();
                return representative;
            }
        };
        return newHierarchy.transform(transformer,null);
    }
    protected Hierarchy<Role> removeInverses(Hierarchy<Role> hierarchy) {
        final Map<Role,GraphNode<Role>> allSubsumers=new HashMap<Role,GraphNode<Role>>();
        Set<Role> toProcess=new HashSet<Role>();
        Set<Role> visited=new HashSet<Role>();
        toProcess.add(m_bottomRoleElement.m_role);
        while (!toProcess.isEmpty()) {
            Role current=toProcess.iterator().next();
            visited.add(current);
            HierarchyNode<Role> currentNode=hierarchy.getNodeForElement(current);
            Set<Role> atomicRepresentatives=new HashSet<Role>();
            findNextHierarchyNodeWithAtomic(atomicRepresentatives, currentNode);
            allSubsumers.put(current,new GraphNode<Role>(current,atomicRepresentatives));
            toProcess.addAll(atomicRepresentatives);
            toProcess.removeAll(visited);
            m_interruptFlag.checkInterrupt();
        }
        Hierarchy<Role> newHierarchy=DeterministicClassification.buildHierarchy(m_topRoleElement.m_role,m_bottomRoleElement.m_role,allSubsumers);
        for (Role element : newHierarchy.m_nodesByElements.keySet()) {
            HierarchyNode<Role> oldNode=hierarchy.getNodeForElement(element);
            HierarchyNode<Role> newNode=newHierarchy.getNodeForElement(element);
            for (Role equivalent : oldNode.m_equivalentElements) {
                if (equivalent instanceof AtomicRole)
                    newNode.m_equivalentElements.add(equivalent);
            }
            m_interruptFlag.checkInterrupt();
        }
        return newHierarchy;
    }
    public void setToClassifiedRoleHierarchy(final Hierarchy<Role> roleHierarchy) {
        m_currentRoleHierarchy=transformRoleHierarchy(roleHierarchy);
        // clean up known and possibles
        if (m_propertiesInitialised && m_individuals.length>0) {
            Queue<HierarchyNode<RoleElement>> toProcess=new LinkedList<HierarchyNode<RoleElement>>();
            toProcess.add(m_currentRoleHierarchy.m_bottomNode);
            while (!toProcess.isEmpty()) {
                HierarchyNode<RoleElement> current=toProcess.remove();
                RoleElement currentRepresentative=current.getRepresentative();
                Set<HierarchyNode<RoleElement>> ancestors=current.getAncestorNodes();
                ancestors.remove(current);
                for (HierarchyNode<RoleElement> ancestor : ancestors) {
                    RoleElement ancestorRepresentative=ancestor.m_representative;
                    Map<Individual,Set<Individual>> ancestorKnowRelations=ancestorRepresentative.m_knownRelations;
                    Map<Individual,Set<Individual>> ancestorPossibleRelations=ancestorRepresentative.m_possibleRelations;
                    for (Individual individual : currentRepresentative.m_knownRelations.keySet()) {
                        Set<Individual> successors=ancestorKnowRelations.get(individual);
                        if (successors!=null) {
                            successors.removeAll(currentRepresentative.m_knownRelations.get(individual));
                            if (successors.isEmpty())
                                ancestorKnowRelations.remove(individual);
                        }
                        successors=ancestorPossibleRelations.get(individual);
                        if (successors!=null) {
                            successors.removeAll(currentRepresentative.m_knownRelations.get(individual));
                            if (successors.isEmpty())
                                ancestorPossibleRelations.remove(individual);
                        }
                    }
                    for (Individual individual : currentRepresentative.m_possibleRelations.keySet()) {
                        Set<Individual> successors=ancestorPossibleRelations.get(individual);
                        if (successors!=null) {
                            successors.removeAll(currentRepresentative.m_possibleRelations.get(individual));
                            if (successors.isEmpty())
                                ancestorPossibleRelations.remove(individual);
                        }
                    }
                }
                for (HierarchyNode<RoleElement> parent : current.getParentNodes())
                    if (!toProcess.contains(parent)) 
                        toProcess.add(parent);
                m_interruptFlag.checkInterrupt();
            }
        }
        m_usesClassifiedObjectRoleHierarchy=true;
    }
    protected void findNextHierarchyNodeWithAtomic(Set<Role> atomicRepresentatives, HierarchyNode<Role> current) {
        for (HierarchyNode<Role> successor : current.getParentNodes()) {
            Set<Role> suitable=new HashSet<Role>();
            for (Role role : successor.getEquivalentElements()) {
                if (role instanceof AtomicRole)
                    suitable.add(role);
            }
            if (!suitable.isEmpty()) {
                atomicRepresentatives.add(suitable.iterator().next());
            } else if (successor!=current)
                findNextHierarchyNodeWithAtomic(atomicRepresentatives, successor);
        }
    }
    public OWLAxiom[] getAxiomsForReadingOffCompexProperties(OWLDataFactory factory, ReasonerProgressMonitor monitor, int completedSteps, int steps) {
        if (m_complexRoles.size()>0) {
            int noAdditionalAxioms=0;
            List<OWLAxiom> additionalAxioms=new ArrayList<OWLAxiom>();
            m_interruptFlag.startTask();
            try {
                for (;m_currentIndividualIndex<m_individuals.length && noAdditionalAxioms < thresholdForAdditionalAxioms;m_currentIndividualIndex++) {
                    Individual ind=m_individuals[m_currentIndividualIndex];
                    for (AtomicRole objectRole : m_complexRoles) {
                        completedSteps++;
                        if (monitor!=null)
                            monitor.reasonerTaskProgressChanged(completedSteps,steps);
                        OWLObjectProperty objectProperty=factory.getOWLObjectProperty(IRI.create(((AtomicRole)objectRole).getIRI()));
                        String indIRI=ind.getIRI();
                        OWLClass classForIndividual=factory.getOWLClass(IRI.create("internal:individual-concept#"+indIRI));
                        OWLAxiom axiom=factory.getOWLClassAssertionAxiom(classForIndividual,factory.getOWLNamedIndividual(IRI.create(indIRI)));
                        additionalAxioms.add(axiom); // A_a(a)
                        AtomicConcept conceptForRole=AtomicConcept.create("internal:individual-concept#"+((AtomicRole)objectRole).getIRI()+"#"+indIRI);
                        OWLClass classForRoleAndIndividual=factory.getOWLClass(IRI.create(conceptForRole.getIRI()));
                        axiom=factory.getOWLSubClassOfAxiom(classForIndividual,factory.getOWLObjectAllValuesFrom(objectProperty,classForRoleAndIndividual));
                        additionalAxioms.add(axiom); // A_a implies forall r.A_a^r
                        noAdditionalAxioms+=2;
                        m_interruptFlag.checkInterrupt();
                    }
                }
            } finally {
                m_interruptFlag.endTask();
            }
            OWLAxiom[] additionalAxiomsArray=new OWLAxiom[additionalAxioms.size()];
            return additionalAxioms.toArray(additionalAxiomsArray);
        } else {
            m_currentIndividualIndex=m_individuals.length-1;
            return new OWLAxiom[0];
        }
    }
    public void initializeKnowAndPossibleClassInstances(Tableau tableau, ReasonerProgressMonitor monitor, int completedSteps, int steps) {
        if (!m_classesInitialised) {
            m_interruptFlag.startTask();
            try {
//                long t=System.currentTimeMillis();
                initializeIndividualsForNodes();
//                System.out.println("Initialise individuals for nodes: "+(System.currentTimeMillis()-t));
                if (!m_propertiesInitialised) {
                    // nothing has been read-off yet
//                    t=System.currentTimeMillis();
                    initializeSameAs();
//                    System.out.println("Initialise same as: "+(System.currentTimeMillis()-t));
                }
//                t=System.currentTimeMillis();
                completedSteps=readOffClassInstancesByIndividual(tableau, monitor, completedSteps, steps);
//                System.out.println("Read off class instances: "+(System.currentTimeMillis()-t));
                if (!m_readingOffFoundPossibleConceptInstance && m_usesClassifiedConceptHierarchy) 
                    m_realizationCompleted=true;
                m_classesInitialised=true;
                m_individualsForNodes.clear();
            } finally {
                m_interruptFlag.endTask();
            }
        }
    }
    protected int readOffClassInstancesByIndividual(Tableau tableau, ReasonerProgressMonitor monitor, int completedSteps, int steps) {
        for (Individual ind : m_individuals) {
            Node nodeForIndividual=m_nodesForIndividuals.get(ind);
            // read of concept instances and normal role instances only once, we don't slice that
            boolean hasType=readOffTypes(ind,nodeForIndividual);
            if (!hasType) {
                AtomicConceptElement topElement=m_conceptToElement.get(m_topConcept);
                if (topElement==null) {
                    System.out.println("Ups, the top element was null");
                    topElement=new AtomicConceptElement(null, null);
                    m_conceptToElement.put(m_topConcept, topElement);
                }
                topElement.m_knownInstances.add(ind);
            }
            completedSteps++;
            if (monitor!=null)
                monitor.reasonerTaskProgressChanged(completedSteps,steps);
            m_interruptFlag.checkInterrupt();
        }
        return completedSteps;
    }
    public int initializeKnowAndPossiblePropertyInstances(Tableau tableau, ReasonerProgressMonitor monitor, int startIndividualIndex, int completedSteps, int steps) {
        if (!m_propertiesInitialised) {
            m_interruptFlag.startTask();
            try {
                initializeIndividualsForNodes();
                if (!m_classesInitialised) {
                    // nothing has been read-off yet
                    initializeSameAs();
                }
                completedSteps=readOffPropertyInstancesByIndividual(tableau,m_individualsForNodes, monitor, completedSteps, steps, startIndividualIndex);
                if (m_currentIndividualIndex>=m_individuals.length-1) {
                    // we are done now with everything
                    if (!m_readingOffFoundPossiblePropertyInstance) 
                        m_roleRealizationCompleted=true;
                    m_propertiesInitialised=true;
                }
                m_individualsForNodes.clear();
            } finally {
                m_interruptFlag.endTask();
            }   
        }
        return completedSteps;
    }
    protected int readOffPropertyInstancesByIndividual(Tableau tableau,Map<Node,Individual> individualsForNodes, ReasonerProgressMonitor monitor, int completedSteps, int steps, int startIndividualIndex) {
        // first round we go over all individuals
        int endIndex=(startIndividualIndex==0) ? m_individuals.length : m_currentIndividualIndex;
        for (int index=startIndividualIndex;index<endIndex;index++) {
            Individual ind=m_individuals[index];
            Node nodeForIndividual=m_nodesForIndividuals.get(ind);
            if (startIndividualIndex==0) {
                // read of concept instances and normal role instances only once, we don't slice that
                readOffPropertyInstances(ind,nodeForIndividual);
                completedSteps++;
                if (monitor!=null)
                    monitor.reasonerTaskProgressChanged(completedSteps,steps);
            }
            // read-off complex role instances only for the slice for which extra axioms have been added
            if (index<m_currentIndividualIndex)
                completedSteps=readOffComplexRoleSuccessors(ind,nodeForIndividual, monitor, completedSteps, steps);
            m_interruptFlag.checkInterrupt();
        }
        return completedSteps;
    }
    protected void initializeIndividualsForNodes() {
        for (Individual ind : m_individuals) {
            Node node=m_nodesForIndividuals.get(ind);
            m_individualsForNodes.put(node, ind);
            m_interruptFlag.checkInterrupt();
        }
    }
    protected void initializeSameAs() {
        m_individualToPossibleEquivalenceClass=new HashMap<Set<Individual>, Set<Set<Individual>>>();
        for (Node node : m_individualsForNodes.keySet()) {
            Node mergedInto=node.getMergedInto();
            if (mergedInto!=null) {
                Individual individual1=m_individualsForNodes.get(node);
                Individual individual2=m_individualsForNodes.get(mergedInto);
                Set<Individual> individual1Equivalences=m_individualToEquivalenceClass.get(individual1);
                Set<Individual> individual2Equivalences=m_individualToEquivalenceClass.get(individual2);
                if (node.getMergedIntoDependencySet().isEmpty()) {
                    individual1Equivalences.addAll(individual2Equivalences);
                    m_individualToEquivalenceClass.put(individual2, individual1Equivalences);
                } else {
                    Set<Set<Individual>> possibleEquivalenceClasses=m_individualToPossibleEquivalenceClass.get(individual1Equivalences);
                    if (possibleEquivalenceClasses==null) {
                        possibleEquivalenceClasses=new HashSet<Set<Individual>>();
                        m_individualToPossibleEquivalenceClass.put(individual1Equivalences,possibleEquivalenceClasses);
                    }
                    possibleEquivalenceClasses.add(individual2Equivalences);
                }
            }
            m_interruptFlag.checkInterrupt();
        }
    }
    protected boolean readOffTypes(Individual ind, Node nodeForIndividual) {
        boolean hasBeenAdded=false;
        m_binaryRetrieval1Bound.getBindingsBuffer()[1]=nodeForIndividual.getCanonicalNode();
        m_binaryRetrieval1Bound.open();
        Object[] tupleBuffer=m_binaryRetrieval1Bound.getTupleBuffer();
        while (!m_binaryRetrieval1Bound.afterLast()) {
            Object predicate=tupleBuffer[0];
            if (predicate instanceof AtomicConcept) {
                AtomicConcept atomicConcept=(AtomicConcept)predicate;
                if (!atomicConcept.equals(m_topConcept) && !Prefixes.isInternalIRI(atomicConcept.getIRI())) {
                    HierarchyNode<AtomicConcept> node=m_currentConceptHierarchy.getNodeForElement(atomicConcept);
                    AtomicConcept representative=node.getRepresentative();
                    AtomicConceptElement element=m_conceptToElement.get(representative);
                    if (element==null) {
                        element=new AtomicConceptElement(null, null);
                        m_conceptToElement.put(representative, element);
                    }
                    hasBeenAdded=true;
                    if (m_binaryRetrieval1Bound.getDependencySet().isEmpty())
                        addKnownConceptInstance(node, element, ind);
                    else {
                        addPossibleConceptInstance(node, element, ind);
                        m_readingOffFoundPossibleConceptInstance=true;
                    }
                }
            }
            m_interruptFlag.checkInterrupt();
            m_binaryRetrieval1Bound.next();
        }
        return hasBeenAdded;
    }
    protected void readOffPropertyInstances(Individual ind, Node nodeForIndividual) {
        m_ternaryRetrieval1Bound.getBindingsBuffer()[1]=nodeForIndividual.getCanonicalNode();
        m_ternaryRetrieval1Bound.open();
        Object[] tupleBuffer=m_ternaryRetrieval1Bound.getTupleBuffer();
        while (!m_ternaryRetrieval1Bound.afterLast()) {
            Object roleObject=tupleBuffer[0];
            if (roleObject instanceof AtomicRole) {
                AtomicRole atomicrole=(AtomicRole)roleObject;
                if (!atomicrole.equals(AtomicRole.TOP_OBJECT_ROLE)) {
                    Node node2=(Node)tupleBuffer[2];
                    if (node2.isActive() && node2.getNodeType()==NodeType.NAMED_NODE && m_individualsForNodes.containsKey(node2)) {
                        Individual successor=m_individualsForNodes.get(node2);
                        RoleElement representative=m_currentRoleHierarchy.getNodeForElement(m_roleElementManager.getRoleElement(atomicrole)).getRepresentative();
                        if (m_ternaryRetrieval1Bound.getDependencySet().isEmpty())
                            addKnownRoleInstance(representative, ind, successor);
                        else {
                            addPossibleRoleInstance(representative, ind, successor);
                            m_readingOffFoundPossiblePropertyInstance=true;
                        }
                    }
                }
            }
            m_interruptFlag.checkInterrupt();
            m_ternaryRetrieval1Bound.next();
        }
    }
    protected int readOffComplexRoleSuccessors(Individual ind, Node nodeForIndividual, ReasonerProgressMonitor monitor, int completedSteps, int steps) {
        String indIRI=ind.getIRI();
        AtomicConcept conceptForRole;
        for (AtomicRole atomicRole : m_complexRoles) {
            conceptForRole=AtomicConcept.create("internal:individual-concept#"+atomicRole.getIRI()+"#"+indIRI);
            m_binaryRetrieval0Bound.getBindingsBuffer()[0]=conceptForRole;
            m_binaryRetrieval0Bound.open();
            Object[] tupleBuffer=m_binaryRetrieval0Bound.getTupleBuffer();
            while (!m_binaryRetrieval0Bound.afterLast()) {
                Node node=(Node)tupleBuffer[1];
                if (node.isActive() && node.getNodeType()==NodeType.NAMED_NODE && m_individualsForNodes.containsKey(node)) {
                    Individual successor=m_individualsForNodes.get(node.getCanonicalNode());
                    RoleElement representative=m_currentRoleHierarchy.getNodeForElement(m_roleElementManager.getRoleElement(atomicRole)).getRepresentative();
                    if (m_binaryRetrieval0Bound.getDependencySet().isEmpty())
                        addKnownRoleInstance(representative, ind, successor);
                    else {
                        addPossibleRoleInstance(representative, ind, successor);
                        m_readingOffFoundPossiblePropertyInstance=true;
                    }
                }
                m_interruptFlag.checkInterrupt();
                m_binaryRetrieval0Bound.next();
            }
            completedSteps++;
            if (monitor!=null)
                monitor.reasonerTaskProgressChanged(completedSteps,steps);
        }
        return completedSteps;
    }
//    protected Individual[][] traverseDepthFirst(HierarchyNode<AtomicConceptElement> node,HierarchyNode<AtomicConceptElement> parentNode,Set<Individual> parentKnownInstances,Set<Individual> parentPossibleInstances,Set<HierarchyNode<AtomicConceptElement>> visited,Map<Node,Individual> individualsForNodes) {
//        boolean firstVisit=visited.add(node);
//        Set<Individual> childKnown=new HashSet<Individual>();
//        Set<Individual> childPossible=new HashSet<Individual>();
//        if (firstVisit && node!=m_currentConceptHierarchy.m_bottomNode) {
//            Set<Individual> knownInstances=new HashSet<Individual>();
//            Set<Individual> possibleInstances=new HashSet<Individual>();
//            //Object[] tupleBuffer;
//            m_binaryRetrieval01Bound.getBindingsBuffer()[0]=node.m_representative.m_atomicConcept;
//            for (Individual knownInstance : parentKnownInstances) {
//                m_binaryRetrieval01Bound.getBindingsBuffer()[1]=m_nodesForIndividuals.get(knownInstance).getCanonicalNode();
//                m_binaryRetrieval01Bound.open();
//                //tupleBuffer=m_binaryRetrieval01Bound.getTupleBuffer();
//                if (!m_binaryRetrieval01Bound.afterLast()) {
//                    if (m_binaryRetrieval01Bound.getDependencySet().isEmpty()) {
//                        knownInstances.add(knownInstance);
//                    } else 
//                        possibleInstances.add(knownInstance);
//                }
//            }
//            for (Individual possibleInstance : parentPossibleInstances) {
//                m_binaryRetrieval01Bound.getBindingsBuffer()[1]=m_nodesForIndividuals.get(possibleInstance).getCanonicalNode();
//                m_binaryRetrieval01Bound.open();
//                //tupleBuffer=m_binaryRetrieval01Bound.getTupleBuffer();
//                if (!m_binaryRetrieval01Bound.afterLast()) {
//                    if (m_binaryRetrieval01Bound.getDependencySet().isEmpty()) {
//                        knownInstances.add(possibleInstance);
//                    } else 
//                        possibleInstances.add(possibleInstance);
//                }
//            }
//            // don't store here what is already stored in a child node
//            for (HierarchyNode<AtomicConceptElement> childNode : node.m_childNodes) {
//                Individual[][] childResult=traverseDepthFirst(childNode,node,knownInstances,possibleInstances,visited,individualsForNodes);
//                childKnown.addAll(Arrays.asList(childResult[0]));
//                childPossible.addAll(Arrays.asList(childResult[1]));
//            }
//            knownInstances.removeAll(childKnown);
//            possibleInstances.removeAll(childKnown);
//            possibleInstances.removeAll(childPossible);
//            node.m_representative.m_knownInstances.addAll(knownInstances);
//            node.m_representative.m_possibleInstances.addAll(possibleInstances);
//            // used to tell the parent what is already stored in descendant nodes
//            childKnown.addAll(knownInstances);
//            childPossible.addAll(possibleInstances);
//        }
//        Individual[][] result=new Individual[2][];
//        result[0]=childKnown.toArray(new Individual[0]);
//        result[1]=childPossible.toArray(new Individual[0]);
//        return result;
//    }
//    @SuppressWarnings("unchecked")
//    protected Object[] traverseDepthFirstRoles(HierarchyNode<RoleElement> node,HierarchyNode<RoleElement> parentNode,Map<Individual,Set<Individual>> parentKnownRelations,Map<Individual,Set<Individual>> parentPossibleRelations,Set<HierarchyNode<RoleElement>> visited,Map<Node,Individual> individualsForNodes,Set<Role> complexObjectRoles) {
//        boolean firstVisit=visited.add(node);
//        Map<Individual,Set<Individual>> descendantKnown=new HashMap<Individual,Set<Individual>>();
//        Map<Individual,Set<Individual>> descendantPossible=new HashMap<Individual,Set<Individual>>();
//        if (firstVisit && node!=m_currentRoleHierarchy.m_bottomNode) {
//            Map<Individual,Set<Individual>> knownInstances=new HashMap<Individual,Set<Individual>>();
//            Map<Individual,Set<Individual>> possibleInstances=new HashMap<Individual,Set<Individual>>();
//            AtomicRole atomicRole=(AtomicRole)node.m_representative.m_role;
//            m_ternaryRetrieval012Bound.getBindingsBuffer()[0]=atomicRole;
//            for (Individual individual : parentKnownRelations.keySet()) {
//                Node individual1Node=m_nodesForIndividuals.get(individual).getCanonicalNode();
//                m_ternaryRetrieval012Bound.getBindingsBuffer()[1]=individual1Node;
//                AtomicConcept conceptForRole=AtomicConcept.create("internal:individual-concept#"+atomicRole.getIRI()+"#"+individualsForNodes.get(individual1Node));
//                for (Individual successor : parentKnownRelations.get(individual)) {
//                    Node individual2Node=m_nodesForIndividuals.get(successor).getCanonicalNode();
//                    m_ternaryRetrieval012Bound.getBindingsBuffer()[2]=individual2Node;
//                    m_ternaryRetrieval012Bound.open();
//                    if (!m_ternaryRetrieval012Bound.afterLast()) {
//                        Map<Individual,Set<Individual>> relevantRelation;
//                        if (m_ternaryRetrieval012Bound.getDependencySet().isEmpty())
//                            relevantRelation=knownInstances;
//                        else 
//                            relevantRelation=possibleInstances;
//                        Set<Individual> successors=relevantRelation.get(individual);
//                        if (successors==null) {
//                            successors=new HashSet<Individual>();
//                            relevantRelation.put(individual, successors);
//                        }
//                        successors.add(successor);
//                    }
//                    if (complexObjectRoles.contains(atomicRole)) {
//                        m_binaryRetrieval01Bound.getBindingsBuffer()[0]=conceptForRole;
//                        m_binaryRetrieval01Bound.getBindingsBuffer()[1]=individual2Node;
//                        m_binaryRetrieval0Bound.open();
//                        if (!m_binaryRetrieval01Bound.afterLast()) {
//                            Map<Individual,Set<Individual>> relevantRelation;
//                            if (m_binaryRetrieval0Bound.getDependencySet().isEmpty())
//                                relevantRelation=knownInstances;
//                            else 
//                                relevantRelation=possibleInstances;
//                            Set<Individual> successors=relevantRelation.get(individual);
//                            if (successors==null) {
//                                successors=new HashSet<Individual>();
//                                relevantRelation.put(individual, successors);
//                            }
//                            successors.add(successor);
//                        }
//                    }
//                }
//            }
//            for (Individual individual : parentPossibleRelations.keySet()) {
//                Node individual1Node=m_nodesForIndividuals.get(individual).getCanonicalNode();
//                m_ternaryRetrieval012Bound.getBindingsBuffer()[1]=individual1Node;
//                AtomicConcept conceptForRole=AtomicConcept.create("internal:individual-concept#"+atomicRole.getIRI()+"#"+individualsForNodes.get(individual1Node));
//                for (Individual successor : parentPossibleRelations.get(individual)) {
//                    Node individual2Node=m_nodesForIndividuals.get(successor).getCanonicalNode();
//                    m_ternaryRetrieval012Bound.getBindingsBuffer()[2]=individual2Node;
//                    m_ternaryRetrieval012Bound.open();
//                    if (!m_ternaryRetrieval012Bound.afterLast()) {
//                        Map<Individual,Set<Individual>> relevantRelation;
//                        if (m_ternaryRetrieval012Bound.getDependencySet().isEmpty())
//                            relevantRelation=knownInstances;
//                        else 
//                            relevantRelation=possibleInstances;
//                        Set<Individual> successors=relevantRelation.get(individual);
//                        if (successors==null) {
//                            successors=new HashSet<Individual>();
//                            relevantRelation.put(individual, successors);
//                        }
//                        successors.add(successor);
//                    }
//                    if (complexObjectRoles.contains(atomicRole)) {
//                        m_binaryRetrieval01Bound.getBindingsBuffer()[0]=conceptForRole;
//                        m_binaryRetrieval01Bound.getBindingsBuffer()[1]=individual2Node;
//                        m_binaryRetrieval0Bound.open();
//                        if (!m_binaryRetrieval01Bound.afterLast()) {
//                            Map<Individual,Set<Individual>> relevantRelation;
//                            if (m_binaryRetrieval0Bound.getDependencySet().isEmpty())
//                                relevantRelation=knownInstances;
//                            else 
//                                relevantRelation=possibleInstances;
//                            Set<Individual> successors=relevantRelation.get(individual);
//                            if (successors==null) {
//                                successors=new HashSet<Individual>();
//                                relevantRelation.put(individual, successors);
//                            }
//                            successors.add(successor);
//                        }
//                    }
//                }
//            }
//            // don't store here what is already stored in a child node
//            for (HierarchyNode<RoleElement> childNode : node.m_childNodes) {
//                Object[] storedInDescendants=traverseDepthFirstRoles(childNode,node,knownInstances,possibleInstances,visited,individualsForNodes,complexObjectRoles);
//                Map<Individual,Set<Individual>> thisDescendantKnown=(HashMap<Individual,Set<Individual>>)storedInDescendants[0];
//                Map<Individual,Set<Individual>> thisDescendantPossible=(HashMap<Individual,Set<Individual>>)storedInDescendants[1];
//                for (Individual individual : thisDescendantKnown.keySet())
//                    if (descendantKnown.containsKey(individual))
//                        descendantKnown.get(individual).addAll(thisDescendantKnown.get(individual));
//                    else
//                        descendantKnown.put(individual, thisDescendantKnown.get(individual));
//                for (Individual individual : thisDescendantPossible.keySet())
//                    if (descendantPossible.containsKey(individual))
//                        descendantPossible.get(individual).addAll(thisDescendantPossible.get(individual));
//                    else
//                        descendantPossible.put(individual, thisDescendantPossible.get(individual));
//            }
//            for (Individual individual : descendantKnown.keySet()) {
//                Set<Individual> successors=knownInstances.get(individual);
//                if (successors!=null) {
//                    successors.removeAll(descendantKnown.get(individual));
//                    if (successors.isEmpty())
//                        knownInstances.remove(individual);
//                }
//                successors=possibleInstances.get(individual);
//                if (successors!=null) {
//                    successors.removeAll(descendantKnown.get(individual));
//                    if (successors.isEmpty())
//                        possibleInstances.remove(individual);
//                }
//            }
//            for (Individual individual : descendantPossible.keySet()) {
//                Set<Individual> successors=possibleInstances.get(individual);
//                if (successors!=null) {
//                    successors.removeAll(descendantPossible.get(individual));
//                    if (successors.isEmpty())
//                        possibleInstances.remove(individual);
//                }
//            }
//            Map<Individual,Set<Individual>> map=node.m_representative.m_knownRelations;
//            for (Individual individual : knownInstances.keySet())
//                map.put(individual, knownInstances.get(individual));
//            map=node.m_representative.m_possibleRelations;
//            for (Individual individual : possibleInstances.keySet())
//                map.put(individual, possibleInstances.get(individual));
//            // used to tell the parent what is already stored in descendant nodes
//            for (Individual individual : knownInstances.keySet()) {
//                Set<Individual> successors=descendantKnown.get(individual);
//                if (successors==null) {
//                    successors=new HashSet<Individual>();
//                    descendantKnown.put(individual, successors);
//                }
//                successors.addAll(knownInstances.get(individual));
//            }
//            for (Individual individual : possibleInstances.keySet()) {
//                Set<Individual> successors=descendantPossible.get(individual);
//                if (successors==null) {
//                    successors=new HashSet<Individual>();
//                    descendantPossible.put(individual, successors);
//                }
//                successors.addAll(possibleInstances.get(individual));
//            }
//        }
//        Object[] result=new Object[2];
//        result[0]=descendantKnown;
//        result[1]=descendantPossible;
//        return result;
//    }
//    @SuppressWarnings("unchecked")
//    protected boolean[] readOfByPredicate(Tableau tableau,Set<Role> complexObjectRoles,Map<Node,Individual> individualsForNodes,Map<Node,Set<Node>> canonicalNodeToOriginalNodes) {
//        boolean[] hasPossibles=new boolean[2];
//        Object[] tupleBuffer;
//        Set<AtomicConcept> concepts=new HashSet<AtomicConcept>(m_atomicConceptElementManager.m_conceptToElement.keySet());
//        concepts.remove(AtomicConcept.THING);
//        concepts.remove(AtomicConcept.NOTHING);
//        Set<HierarchyNode<AtomicConceptElement>> visited=new HashSet<HierarchyNode<AtomicConceptElement>>();
//        Set<Individual> topInstances=new HashSet<Individual>(Arrays.asList(m_individuals));
//        HierarchyNode<AtomicConceptElement> topNode=m_currentConceptHierarchy.getNodeForElement(m_topConceptElement);
//        for (HierarchyNode<AtomicConceptElement> topChild : topNode.m_childNodes) {
//            Set<Individual> knownInstances=new HashSet<Individual>();
//            Set<Individual> possibleInstances=new HashSet<Individual>();
//            m_binaryRetrieval0Bound.getBindingsBuffer()[0]=topChild.m_representative.m_atomicConcept;
//            m_binaryRetrieval0Bound.open();
//            tupleBuffer=m_binaryRetrieval0Bound.getTupleBuffer();
//            while (!m_binaryRetrieval0Bound.afterLast()) {
//                Node individualNode=(Node)tupleBuffer[1];
//                if (individualNode.getNodeType()==NodeType.NAMED_NODE && individualNode.isActive() && individualsForNodes.containsKey(individualNode)) {
//                    Set<Individual> relevantIndividuals=new HashSet<Individual>();
//                    Set<Individual> possiblyRelevantIndividuals=new HashSet<Individual>();
//                    relevantIndividuals.add(individualsForNodes.get(individualNode));
//                    if (canonicalNodeToOriginalNodes.containsKey(individualNode)) 
//                        for (Node mergedNode : canonicalNodeToOriginalNodes.get(individualNode)) { 
//                            if (mergedNode.getMergedIntoDependencySet().isEmpty()) 
//                                relevantIndividuals.add(individualsForNodes.get(mergedNode));
//                            else {
//                                possiblyRelevantIndividuals.add(individualsForNodes.get(mergedNode));
//                                hasPossibles[0]=true;
//                            }
//                        }
//                    for (Individual individual : relevantIndividuals)
//                        if (m_binaryRetrieval0Bound.getDependencySet().isEmpty())
//                            knownInstances.add(individual);
//                        else {
//                            possibleInstances.add(individual);
//                            hasPossibles[0]=true;
//                        }
//                    for (Individual individual : possiblyRelevantIndividuals) {
//                        possibleInstances.add(individual);
//                        hasPossibles[0]=true;
//                    }
//                }
//                m_binaryRetrieval0Bound.next();
//            }
//            topInstances.removeAll(knownInstances);
//            topInstances.removeAll(possibleInstances);
//            // find out which ones are stored at a descendant already
//            Set<Individual> descendantKnown=new HashSet<Individual>();
//            Set<Individual> descendantPossible=new HashSet<Individual>();
//            for (HierarchyNode<AtomicConceptElement> child : topChild.m_childNodes) {
//                Individual[][] storedInDescendants=traverseDepthFirst(child, topChild, knownInstances, possibleInstances, visited, individualsForNodes);
//                descendantKnown.addAll(Arrays.asList(storedInDescendants[0]));
//                descendantPossible.addAll(Arrays.asList(storedInDescendants[1]));
//            }
//            knownInstances.removeAll(descendantKnown);
//            possibleInstances.removeAll(descendantKnown);
//            possibleInstances.removeAll(descendantPossible);
//            if (!descendantPossible.isEmpty()) 
//                hasPossibles[0]=true;
//            topChild.m_representative.m_knownInstances.addAll(knownInstances);
//            topChild.m_representative.m_possibleInstances.addAll(possibleInstances);
//        }
//        topNode.m_representative.m_knownInstances.addAll(topInstances); // used in complex concept instance retrieval, where we traverse also the ancestor nodes and test the individuals stored there
//        
//        //roles
//        Set<Role> roles=new HashSet<Role>(m_roleElementManager.m_roleToElement.keySet());
//        roles.remove(AtomicRole.TOP_OBJECT_ROLE);
//        roles.remove(AtomicRole.BOTTOM_OBJECT_ROLE);
//        Set<HierarchyNode<RoleElement>> visitedRoles=new HashSet<HierarchyNode<RoleElement>>();
//        HierarchyNode<RoleElement> topRoleNode=m_currentRoleHierarchy.getNodeForElement(m_topRoleElement);
//        for (HierarchyNode<RoleElement> topChild : topRoleNode.m_childNodes) {
//            Role role=topChild.m_representative.m_role;
//            Map<Individual,Set<Individual>> knownInstances=new HashMap<Individual,Set<Individual>>();
//            Map<Individual,Set<Individual>> possibleInstances=new HashMap<Individual,Set<Individual>>();
//            m_ternaryRetrieval0Bound.getBindingsBuffer()[0]=role;
//            m_ternaryRetrieval0Bound.open();
//            tupleBuffer=m_ternaryRetrieval0Bound.getTupleBuffer();
//            while (!m_ternaryRetrieval0Bound.afterLast()) {
//                Node individual1Node=(Node)tupleBuffer[1];
//                Node individual2Node=(Node)tupleBuffer[2];
//                if (individual1Node.getNodeType()==NodeType.NAMED_NODE && individual2Node.getNodeType()==NodeType.NAMED_NODE && individual1Node.isActive() && individual2Node.isActive()) {
//                    hasPossibles[1]=computeKnownAndPossibles(knownInstances,possibleInstances,individual1Node,individual2Node,individualsForNodes,canonicalNodeToOriginalNodes,m_ternaryRetrieval0Bound.getDependencySet().isEmpty());
//                }
//                m_ternaryRetrieval0Bound.next();
//            }
//            if (complexObjectRoles.contains(role)) {
//                AtomicRole atomicRole=(AtomicRole)role;
//                for (Node node : individualsForNodes.keySet()) {
//                    if (node.isActive() && node.getMergedInto()==null) {
//                        Individual individual=individualsForNodes.get(node);
//                        AtomicConcept conceptForRole=AtomicConcept.create("internal:individual-concept#"+atomicRole.getIRI()+"#"+individual.getIRI());
//                        m_binaryRetrieval0Bound.getBindingsBuffer()[0]=conceptForRole;
//                        m_binaryRetrieval0Bound.open();
//                        tupleBuffer=m_binaryRetrieval0Bound.getTupleBuffer();
//                        while (!m_binaryRetrieval0Bound.afterLast()) {
//                            Node individual2Node=((Node)tupleBuffer[1]).getCanonicalNode();
//                            if (individual2Node.isActive() && individual2Node.getNodeType()==NodeType.NAMED_NODE) {
//                                hasPossibles[1]=computeKnownAndPossibles(knownInstances,possibleInstances,node,individual2Node,individualsForNodes,canonicalNodeToOriginalNodes,m_binaryRetrieval0Bound.getDependencySet().isEmpty());
//                            }
//                            m_binaryRetrieval0Bound.next();
//                        }
//                    }
//                }
//            }
//            
//            // find out which ones are stored at a descendant already
//            Map<Individual,Set<Individual>> descendantKnown=new HashMap<Individual,Set<Individual>>();
//            Map<Individual,Set<Individual>> descendantPossible=new HashMap<Individual,Set<Individual>>();
//            for (HierarchyNode<RoleElement> child : topChild.m_childNodes) {
//                Object[] storedInDescendants=traverseDepthFirstRoles(child, topChild, knownInstances, possibleInstances, visitedRoles, individualsForNodes, complexObjectRoles);
//                Map<Individual,Set<Individual>> thisDescendantKnown=(HashMap<Individual,Set<Individual>>)storedInDescendants[0];
//                for (Individual individual : thisDescendantKnown.keySet())
//                    if (descendantKnown.containsKey(individual))
//                        descendantKnown.get(individual).addAll(thisDescendantKnown.get(individual));
//                    else
//                        descendantKnown.put(individual, thisDescendantKnown.get(individual));
//                Map<Individual,Set<Individual>> thisDescendantPossible=(HashMap<Individual,Set<Individual>>)storedInDescendants[1];
//                for (Individual individual : thisDescendantPossible.keySet())
//                    if (descendantPossible.containsKey(individual))
//                        descendantPossible.get(individual).addAll(thisDescendantPossible.get(individual));
//                    else
//                        descendantPossible.put(individual, thisDescendantPossible.get(individual));
//            }
//            if (!descendantPossible.isEmpty())
//                hasPossibles[1]=true;
//            
//            for (Individual individual : descendantKnown.keySet()) {
//                Set<Individual> successors=knownInstances.get(individual);
//                if (successors!=null) {
//                    successors.removeAll(descendantKnown.get(individual));
//                    if (successors.isEmpty())
//                        knownInstances.remove(individual);
//                }
//                successors=possibleInstances.get(individual);
//                if (successors!=null) {
//                    successors.removeAll(descendantKnown.get(individual));
//                    if (successors.isEmpty())
//                        possibleInstances.remove(individual);
//                }
//            }
//            for (Individual individual : descendantPossible.keySet()) {
//                Set<Individual> successors=possibleInstances.get(individual);
//                if (successors!=null) {
//                    successors.removeAll(descendantPossible.get(individual));
//                    if (successors.isEmpty())
//                        possibleInstances.remove(individual);
//                }
//            }
//            
//            Map<Individual,Set<Individual>> map=topChild.m_representative.m_knownRelations;
//            for (Individual individual : knownInstances.keySet())
//                map.put(individual, knownInstances.get(individual));
//            map=topChild.m_representative.m_possibleRelations;
//            for (Individual individual : possibleInstances.keySet())
//                map.put(individual, possibleInstances.get(individual));
//        }
//        return hasPossibles;
//    }
//    protected boolean computeKnownAndPossibles(Map<Individual,Set<Individual>> knownInstances,Map<Individual,Set<Individual>> possibleInstances,Node individual1Node,Node individual2Node,Map<Node,Individual> individualsForNodes,Map<Node,Set<Node>> canonicalNodeToOriginalNodes,boolean isDeterministicFact) {
//        boolean hasPossibles=false;
//        Set<Individual> relevantIndividual1s=new HashSet<Individual>();
//        Set<Individual> possiblyRelevantIndividual1s=new HashSet<Individual>();
//        Set<Individual> relevantIndividual2s=new HashSet<Individual>();
//        Set<Individual> possiblyRelevantIndividual2s=new HashSet<Individual>();
//        if (isDeterministicFact) {
//            relevantIndividual1s.add(individualsForNodes.get(individual1Node));
//            relevantIndividual2s.add(individualsForNodes.get(individual2Node));
//        } else {
//            isDeterministicFact=false;
//            hasPossibles=true;
//            possiblyRelevantIndividual1s.add(individualsForNodes.get(individual1Node));
//            possiblyRelevantIndividual2s.add(individualsForNodes.get(individual2Node));
//        } 
//        if (canonicalNodeToOriginalNodes.containsKey(individual1Node)) 
//            for (Node mergedNode : canonicalNodeToOriginalNodes.get(individual1Node)) 
//                if (isDeterministicFact && mergedNode.getMergedIntoDependencySet().isEmpty()) 
//                    relevantIndividual1s.add(individualsForNodes.get(mergedNode));
//                else {
//                    possiblyRelevantIndividual1s.add(individualsForNodes.get(mergedNode));
//                    hasPossibles=true;
//                }
//        if (canonicalNodeToOriginalNodes.containsKey(individual2Node)) 
//            for (Node mergedNode : canonicalNodeToOriginalNodes.get(individual2Node)) 
//                if (isDeterministicFact && mergedNode.getMergedIntoDependencySet().isEmpty()) 
//                    relevantIndividual2s.add(individualsForNodes.get(mergedNode));
//                else {
//                    possiblyRelevantIndividual2s.add(individualsForNodes.get(mergedNode));
//                    hasPossibles=true;
//                }
//        for (Individual individual : relevantIndividual1s) {
//            if (!relevantIndividual2s.isEmpty()) {
//                Set<Individual> knownSuccessors=knownInstances.get(individual);
//                if (knownSuccessors==null) {
//                    knownSuccessors=new HashSet<Individual>();
//                    knownInstances.put(individual, knownSuccessors);
//                }
//                for (Individual successor : relevantIndividual2s)
//                    knownSuccessors.add(successor);
//            }
//        }
//        for (Individual individual : relevantIndividual1s) {
//            if (!possiblyRelevantIndividual2s.isEmpty()) {
//                Set<Individual> possibleSuccessors=possibleInstances.get(individual);
//                if (possibleSuccessors==null) {
//                    possibleSuccessors=new HashSet<Individual>();
//                    possibleInstances.put(individual, possibleSuccessors);
//                }
//                for (Individual successor : possiblyRelevantIndividual2s)
//                    possibleSuccessors.add(successor);
//            }
//        }
//        for (Individual individual : possiblyRelevantIndividual1s) {
//            if (!relevantIndividual2s.isEmpty()) {
//                Set<Individual> possibleSuccessors=possibleInstances.get(individual);
//                if (possibleSuccessors==null) {
//                    possibleSuccessors=new HashSet<Individual>();
//                    possibleInstances.put(individual, possibleSuccessors);
//                }
//                for (Individual successor : relevantIndividual2s)
//                    possibleSuccessors.add(successor);
//            }
//        }
//        for (Individual individual : possiblyRelevantIndividual1s) {
//            if (!possiblyRelevantIndividual2s.isEmpty()) {
//                Set<Individual> possibleSuccessors=possibleInstances.get(individual);
//                if (possibleSuccessors==null) {
//                    possibleSuccessors=new HashSet<Individual>();
//                    possibleInstances.put(individual, possibleSuccessors);
//                }
//                for (Individual successor : possiblyRelevantIndividual2s)
//                    possibleSuccessors.add(successor);
//            }
//        }
//        return hasPossibles;
//    }
    protected void addKnownConceptInstance(HierarchyNode<AtomicConcept> currentNode, AtomicConceptElement element, Individual instance) {
        Set<HierarchyNode<AtomicConcept>> nodes=currentNode.getDescendantNodes();
        for (HierarchyNode<AtomicConcept> node : nodes) {
            AtomicConceptElement descendantElement=m_conceptToElement.get(node.getRepresentative());
            if (descendantElement!=null && descendantElement.m_knownInstances.contains(instance))
                return;
            m_interruptFlag.checkInterrupt();
        }
        element.m_knownInstances.add(instance);
        nodes=currentNode.getAncestorNodes();
        nodes.remove(currentNode);
        for (HierarchyNode<AtomicConcept> node : nodes) {
            AtomicConceptElement ancestorElement=m_conceptToElement.get(node.getRepresentative());
            if (ancestorElement!=null) {
                ancestorElement.m_knownInstances.remove(instance);
                ancestorElement.m_possibleInstances.remove(instance);
            }
        }
    }
    protected void addPossibleConceptInstance(HierarchyNode<AtomicConcept> currentNode, AtomicConceptElement element, Individual instance) {
        Set<HierarchyNode<AtomicConcept>> nodes=currentNode.getDescendantNodes();
        for (HierarchyNode<AtomicConcept> node : nodes) {
            AtomicConceptElement descendantElement=m_conceptToElement.get(node.getRepresentative());
            if (descendantElement!=null && (descendantElement.m_knownInstances.contains(instance) || descendantElement.m_possibleInstances.contains(instance)))
                return;
            m_interruptFlag.checkInterrupt();
        }
        element.m_possibleInstances.add(instance);
        nodes=currentNode.getAncestorNodes();
        nodes.remove(currentNode);
        for (HierarchyNode<AtomicConcept> node : nodes) {
            AtomicConceptElement ancestorElement=m_conceptToElement.get(node.getRepresentative());
            if (ancestorElement!=null) {
                ancestorElement.m_possibleInstances.remove(instance);
                if (ancestorElement.m_possibleInstances.isEmpty() && ancestorElement.m_knownInstances.isEmpty() && node.getRepresentative()!=m_topConcept)
                    m_conceptToElement.remove(node.getRepresentative());
            }
            m_interruptFlag.checkInterrupt();
        }
    }
    protected void addKnownRoleInstance(RoleElement element, Individual individual1, Individual individual2) {
        if (!element.equals(m_topRoleElement)) {
            HierarchyNode<RoleElement> currentNode=m_currentRoleHierarchy.getNodeForElement(element);
            Set<HierarchyNode<RoleElement>> nodes=currentNode.getDescendantNodes();
            for (HierarchyNode<RoleElement> node : nodes) {
                for (RoleElement descendantElement : node.getEquivalentElements()) {
                    if (descendantElement.isKnown(individual1,individual2)) 
                        return;
                }
                m_interruptFlag.checkInterrupt();
            }
            element.addKnown(individual1, individual2);
            nodes=currentNode.getAncestorNodes();
            nodes.remove(currentNode);
            for (HierarchyNode<RoleElement> node : nodes) {
                node.getRepresentative().removeKnown(individual1, individual2);
                m_interruptFlag.checkInterrupt();
            }
        }
    }
    protected void addPossibleRoleInstance(RoleElement element, Individual individual1, Individual individual2) {
        if (!element.equals(m_topRoleElement)) {
            HierarchyNode<RoleElement> currentNode=m_currentRoleHierarchy.getNodeForElement(element);
            Set<HierarchyNode<RoleElement>> nodes=currentNode.getDescendantNodes();
            for (HierarchyNode<RoleElement> node : nodes) {
                for (RoleElement descendantElement : node.getEquivalentElements()) {
                    if (descendantElement.isPossible(individual1, individual2)) 
                        return;
                }
                m_interruptFlag.checkInterrupt();
            }
            element.addPossible(individual1, individual2);
            nodes=currentNode.getAncestorNodes();
            nodes.remove(currentNode);
            for (HierarchyNode<RoleElement> node : nodes) {
                for (RoleElement ancestorElement : node.getEquivalentElements()) {
                    if (ancestorElement.isPossible(individual1,individual2)) 
                        ancestorElement.removePossible(individual1, individual2);
                }
                m_interruptFlag.checkInterrupt();
            }
        }
    }
    public void setInconsistent() {
        m_isInconsistent=true;
        m_realizationCompleted=true;
        m_roleRealizationCompleted=true;
        m_usesClassifiedConceptHierarchy=true;
        m_usesClassifiedObjectRoleHierarchy=true;
        m_currentConceptHierarchy=null;
        m_currentRoleHierarchy=null;
    }
    public void realize(ReasonerProgressMonitor monitor) {
        assert m_usesClassifiedConceptHierarchy==true;
        if (m_readingOffFoundPossibleConceptInstance && !m_realizationCompleted) {
            if (monitor!=null)
                monitor.reasonerTaskStarted("Computing instances for all classes");
            int numHierarchyNodes=m_currentConceptHierarchy.m_nodesByElements.values().size();
            int currentHierarchyNode=0;
            Queue<HierarchyNode<AtomicConcept>> toProcess=new LinkedList<HierarchyNode<AtomicConcept>>();
            Set<HierarchyNode<AtomicConcept>> visited=new HashSet<HierarchyNode<AtomicConcept>>();
            toProcess.addAll(m_currentConceptHierarchy.m_bottomNode.m_parentNodes);
            int possibles=0;
            while (!toProcess.isEmpty()) {
                if (monitor!=null)
                    monitor.reasonerTaskProgressChanged(currentHierarchyNode,numHierarchyNodes);
                HierarchyNode<AtomicConcept> current=toProcess.remove();
                visited.add(current);
                currentHierarchyNode++;
                AtomicConcept atomicConcept=current.getRepresentative();
                AtomicConceptElement atomicConceptElement=m_conceptToElement.get(atomicConcept);
                if (atomicConceptElement!=null) {
                    Set<HierarchyNode<AtomicConcept>> parents=current.getParentNodes();
                    for (HierarchyNode<AtomicConcept> parent : parents) {
                        if (!visited.contains(parent) && !toProcess.contains(parent)) 
                            toProcess.add(parent);
                    }
                    if (atomicConceptElement.hasPossibles()) {
                        possibles+=atomicConceptElement.m_possibleInstances.size();
                        Set<Individual> nonInstances=new HashSet<Individual>();
                        for (Individual individual : atomicConceptElement.getPossibleInstances()) {
                            if (isInstance(individual, atomicConcept)) 
                                atomicConceptElement.m_knownInstances.add(individual);
                            else {
                                nonInstances.add(individual);
                            }
                        }
                        atomicConceptElement.m_possibleInstances.clear();
                        for (HierarchyNode<AtomicConcept> parent : parents) {
                            AtomicConcept parentRepresentative=parent.getRepresentative();
                            AtomicConceptElement parentElement=m_conceptToElement.get(parentRepresentative);
                            if (parentElement==null) {
                                parentElement=new AtomicConceptElement(null, nonInstances);
                                m_conceptToElement.put(parentRepresentative, parentElement);
                            } else if (parentRepresentative.equals(m_topConcept))
                                m_conceptToElement.get(m_topConcept).m_knownInstances.addAll(nonInstances);
                            else 
                                parentElement.addPossibles(nonInstances);
                        }
                    }
                }
                m_interruptFlag.checkInterrupt();
            }
            if (monitor!=null)
                monitor.reasonerTaskStopped();
        }
        m_realizationCompleted=true;
    }
    public void realizeObjectRoles(ReasonerProgressMonitor monitor) {
        if (m_readingOffFoundPossiblePropertyInstance && !m_roleRealizationCompleted) {
            if (monitor!=null)
                monitor.reasonerTaskStarted("Computing instances for all object properties...");
            int numHierarchyNodes=m_currentRoleHierarchy.m_nodesByElements.values().size();
            int currentHierarchyNode=0;
            Queue<HierarchyNode<RoleElement>> toProcess=new LinkedList<HierarchyNode<RoleElement>>();
            Set<HierarchyNode<RoleElement>> visited=new HashSet<HierarchyNode<RoleElement>>();
            toProcess.add(m_currentRoleHierarchy.m_bottomNode);
            while (!toProcess.isEmpty()) {
                if (monitor!=null)
                    monitor.reasonerTaskProgressChanged(currentHierarchyNode,numHierarchyNodes);
                HierarchyNode<RoleElement> current=toProcess.remove();
                visited.add(current);
                currentHierarchyNode++;
                RoleElement roleElement=current.getRepresentative();
                Role role=roleElement.getRole();
                Set<HierarchyNode<RoleElement>> parents=current.getParentNodes();
                for (HierarchyNode<RoleElement> parent : parents)
                    if (!toProcess.contains(parent) && !visited.contains(parent)) 
                        toProcess.add(parent);
                if (roleElement.hasPossibles()) {
                    for (Individual individual : roleElement.m_possibleRelations.keySet()) {
                        Set<Individual> nonInstances=new HashSet<Individual>();
                        for (Individual successor : roleElement.m_possibleRelations.get(individual)) {
                            if (isRoleInstance(role, individual, successor)) 
                                roleElement.addKnown(individual, successor);
                            else {
                                nonInstances.add(individual);
                            }
                        }
                        for (HierarchyNode<RoleElement> parent : parents) {
                            RoleElement parentRepresentative=parent.getRepresentative();
                            if (!parentRepresentative.equals(m_topRoleElement))
                                parentRepresentative.addPossibles(individual, nonInstances);
                        }
                    }
                    roleElement.m_possibleRelations.clear();
                }
                m_interruptFlag.checkInterrupt();
            }
            if (monitor!=null)
                monitor.reasonerTaskStopped();
        }
        m_roleRealizationCompleted=true;
    }
    public Set<HierarchyNode<AtomicConcept>> getTypes(Individual individual,boolean direct) {
        if (m_isInconsistent) 
            return Collections.singleton(m_currentConceptHierarchy.m_bottomNode);
        assert !direct || m_usesClassifiedConceptHierarchy;
        Set<HierarchyNode<AtomicConcept>> result=new HashSet<HierarchyNode<AtomicConcept>>();
        Queue<HierarchyNode<AtomicConcept>> toProcess=new LinkedList<HierarchyNode<AtomicConcept>>();
        toProcess.add(m_currentConceptHierarchy.m_bottomNode);
        while (!toProcess.isEmpty()) {
            HierarchyNode<AtomicConcept> current=toProcess.remove();
            Set<HierarchyNode<AtomicConcept>> parents=current.getParentNodes();
            AtomicConcept atomicConcept=current.getRepresentative();
            AtomicConceptElement atomicConceptElement=m_conceptToElement.get(atomicConcept);
            if (atomicConceptElement!=null && atomicConceptElement.isPossible(individual)) {
                if (isInstance(individual, atomicConcept)) { 
                    atomicConceptElement.setToKnown(individual);
                } else {
                    for (HierarchyNode<AtomicConcept> parent : parents) {
                        AtomicConcept parentRepresentative=parent.getRepresentative();
                        AtomicConceptElement parentElement=m_conceptToElement.get(parentRepresentative);
                        if (parentElement==null) {
                            parentElement=new AtomicConceptElement(null, null);
                            m_conceptToElement.put(parentRepresentative,parentElement);
                        }
                        parentElement.addPossible(individual);
                    }
                }
            }
            if (atomicConceptElement!=null && atomicConceptElement.isKnown(individual)) {
                if (direct)
                    result.add(current);
                else 
                    result.addAll(current.getAncestorNodes());
            } else {
                for (HierarchyNode<AtomicConcept> parent : parents)
                    if (!toProcess.contains(parent)) 
                        toProcess.add(parent);
            }
        }
        return result;
    }
    public boolean hasType(Individual individual,AtomicConcept atomicConcept,boolean direct) {
        HierarchyNode<AtomicConcept> node=m_currentConceptHierarchy.getNodeForElement(atomicConcept);
        return hasType(individual, node, direct);
    }
    public boolean hasType(Individual individual,HierarchyNode<AtomicConcept> node,boolean direct) {
        assert !direct || m_usesClassifiedConceptHierarchy;
        AtomicConcept representative=node.getRepresentative();
        if (representative==m_bottomConcept) 
            return false;
        AtomicConceptElement element=m_conceptToElement.get(representative);
        if ((element!=null && element.isKnown(individual)) || (!direct && node==m_currentConceptHierarchy.m_topNode)) 
            return true;
        if (element!=null && element.isPossible(individual)) {
            if (isInstance(individual, representative)) { 
                element.setToKnown(individual);
                return true;
            } else {
                element.m_possibleInstances.remove(individual);
                if (element.m_knownInstances.isEmpty() && element.m_possibleInstances.isEmpty() && representative!=m_topConcept) 
                    m_conceptToElement.remove(representative);
                for (HierarchyNode<AtomicConcept> parent : node.getParentNodes()) {
                    AtomicConcept parentConcept=parent.getRepresentative();
                    AtomicConceptElement parentElement=m_conceptToElement.get(parentConcept);
                    if (parentElement==null) {
                        parentElement=new AtomicConceptElement(null, null);
                        m_conceptToElement.put(parentConcept, parentElement);
                    }
                    parentElement.addPossible(individual);
                }
            }
        } else if (!direct)
            for (HierarchyNode<AtomicConcept> child : node.getChildNodes())
                if (hasType(individual, child, false))
                    return true;
        return false;
    }
    public Set<Individual> getInstances(AtomicConcept atomicConcept, boolean direct) {
        Set<Individual> result=new HashSet<Individual>();
        HierarchyNode<AtomicConcept> node=m_currentConceptHierarchy.getNodeForElement(atomicConcept);
        if (node==null) return result; // unknown concept
        getInstancesForNode(node,result,direct);
        return result;
    }
    public Set<Individual> getInstances(HierarchyNode<AtomicConcept> node,boolean direct) {
        Set<Individual> result=new HashSet<Individual>();
        HierarchyNode<AtomicConcept> nodeFromCurrentHierarchy=m_currentConceptHierarchy.getNodeForElement(node.m_representative);
        if (nodeFromCurrentHierarchy==null) {
            // complex concept instances
            if (!direct) {
                for (HierarchyNode<AtomicConcept> child : node.getChildNodes()) {
                    getInstancesForNode(child, result, direct);
                }
            }
        } else
            getInstancesForNode(nodeFromCurrentHierarchy, result, direct);
        return result;
    }
    protected void getInstancesForNode(HierarchyNode<AtomicConcept> node,Set<Individual> result,boolean direct) {
        assert !direct || m_usesClassifiedConceptHierarchy;
        AtomicConcept representative=node.getRepresentative();
        if (!direct && representative.equals(m_topConcept)) {
            for (Individual individual : m_individuals)
                if (isResultRelevantIndividual(individual))
                    result.add(individual);
            return;
        }
        AtomicConceptElement representativeElement=m_conceptToElement.get(representative);
        if (representativeElement!=null) {
            Set<Individual> possibleInstances=representativeElement.getPossibleInstances();
            if (!possibleInstances.isEmpty()) {
                for (Individual possibleInstance : new HashSet<Individual>(possibleInstances)) {
                    if (isInstance(possibleInstance, representative))
                        representativeElement.setToKnown(possibleInstance);
                    else {
                        representativeElement.m_possibleInstances.remove(possibleInstance);
                        if (representativeElement.m_knownInstances.isEmpty() && representativeElement.m_possibleInstances.isEmpty() && representative!=m_topConcept) 
                            m_conceptToElement.remove(representative);
                        for (HierarchyNode<AtomicConcept> parent : node.getParentNodes()) {
                            AtomicConcept parentConcept=parent.getRepresentative();
                            AtomicConceptElement parentElement=m_conceptToElement.get(parentConcept);
                            if (parentElement==null) {
                                parentElement=new AtomicConceptElement(null, null);
                                m_conceptToElement.put(parentConcept, parentElement);
                            }
                            parentElement.addPossible(possibleInstance);
                        }
                    }
                }
            }
            for (Individual individual : representativeElement.getKnownInstances()) {
                if (isResultRelevantIndividual(individual)) {
                    boolean isDirect=true;
                    if (direct) {
                        for (HierarchyNode<AtomicConcept> child : node.getChildNodes()) {
                            if (hasType(individual, child, false)) {
                                isDirect=false;
                                break;
                            }
                        }
                    }
                    if (!direct || isDirect) 
                        result.add(individual);
                }
            }
        }
        if (!direct)
            for (HierarchyNode<AtomicConcept> child : node.getChildNodes())
                if (child!=m_currentConceptHierarchy.m_bottomNode) 
                    getInstancesForNode(child, result, false);
    }
    
    public boolean hasObjectRoleRelationship(AtomicRole role, Individual individual1, Individual individual2) {
        RoleElement element=m_roleElementManager.getRoleElement(role);
        HierarchyNode<RoleElement> currentNode=m_currentRoleHierarchy.getNodeForElement(element);
        return hasObjectRoleRelationship(currentNode, individual1, individual2);
    }
    public boolean hasObjectRoleRelationship(HierarchyNode<RoleElement> node,Individual individual1,Individual individual2) {
        RoleElement representativeElement=node.getRepresentative();
        if (representativeElement.isKnown(individual1, individual2) || representativeElement.equals(m_topRoleElement)) 
            return true;
        List<Individual> individuals=Arrays.asList(m_individuals);
        boolean containsUnknown=!individuals.contains(individual1) || !individuals.contains(individual2);
        if (representativeElement.isPossible(individual1,individual2) || containsUnknown) {
            if (isRoleInstance(representativeElement.getRole(),individual1,individual2)) { 
                if (!containsUnknown)
                    representativeElement.setToKnown(individual1,individual2);
                return true;
            } else
                for (HierarchyNode<RoleElement> parent : node.getParentNodes())
                    parent.getRepresentative().addPossible(individual1,individual2);
        } else 
            for (HierarchyNode<RoleElement> child : node.getChildNodes())
                if (hasObjectRoleRelationship(child,individual1,individual2))
                    return true;
        return false;
    }
    public Map<Individual,Set<Individual>> getObjectPropertyInstances(AtomicRole role) {
        Map<Individual,Set<Individual>> result=new HashMap<Individual, Set<Individual>>();
        HierarchyNode<RoleElement> node=m_currentRoleHierarchy.getNodeForElement(m_roleElementManager.getRoleElement(role));
        getObjectPropertyInstances(node,result);
        return result;
    }
    protected void getObjectPropertyInstances(HierarchyNode<RoleElement> node,Map<Individual,Set<Individual>> result) {
        RoleElement representativeElement=node.getRepresentative();
        if (representativeElement.equals(m_topRoleElement) || m_isInconsistent) {
            Set<Individual> allResultRelevantIndividuals=new HashSet<Individual>();
            for (Individual individual : m_individuals)
                if (isResultRelevantIndividual(individual)) {
                    allResultRelevantIndividuals.add(individual);
                    result.put(individual, allResultRelevantIndividuals);
                }
            return;
        }
        Map<Individual,Set<Individual>> possibleInstances=representativeElement.getPossibleRelations();
        for (Individual possibleInstance : new HashSet<Individual>(possibleInstances.keySet())) {
            for (Individual possibleSuccessor : new HashSet<Individual>(possibleInstances.get(possibleInstance))) { 
                if (isRoleInstance(representativeElement.getRole(),possibleInstance,possibleSuccessor))
                    representativeElement.setToKnown(possibleInstance,possibleSuccessor);
                else
                    for (HierarchyNode<RoleElement> parent : node.getParentNodes())
                        parent.getRepresentative().addPossible(possibleInstance,possibleSuccessor);
            }
        }
        Map<Individual,Set<Individual>> knownInstances=representativeElement.getKnownRelations();
        for (Individual instance1 : knownInstances.keySet()) {
            if (isResultRelevantIndividual(instance1)) {
                Set<Individual> successors=result.get(instance1);
                boolean isNew=false;
                if (successors==null) {
                    successors=new HashSet<Individual>();
                    isNew=true;
                }
                for (Individual instance2 : knownInstances.get(instance1)) {
                    if (isResultRelevantIndividual(instance2)) {
                        successors.add(instance2);
                    }
                }
                if (isNew && !successors.isEmpty())
                    result.put(instance1, successors);
            }
        }
        for (HierarchyNode<RoleElement> child : node.getChildNodes())
            getObjectPropertyInstances(child, result);
    }
    public Set<Individual> getObjectPropertyValues(Role role,Individual individual) {
        Set<Individual> result=new HashSet<Individual>();
        HierarchyNode<RoleElement> node;
        if (role instanceof AtomicRole) {
            node=m_currentRoleHierarchy.getNodeForElement(m_roleElementManager.getRoleElement(role));
            getObjectPropertyValues(node,individual, result);
        } else {
            node=m_currentRoleHierarchy.getNodeForElement(m_roleElementManager.getRoleElement(((InverseRole)role).getInverseOf()));
            getObjectPropertySubjects(node, individual, result);
        }
        return result;
    }
    protected void getObjectPropertySubjects(HierarchyNode<RoleElement> node, Individual object, Set<Individual> result) {
        RoleElement representativeElement=node.getRepresentative();
        if (representativeElement.equals(m_topRoleElement) || m_isInconsistent) {
            for (Individual ind : m_individuals)
                if (isResultRelevantIndividual(ind))
                    result.add(ind);
            return;
        } 
        Map<Individual,Set<Individual>> relevantRelations=representativeElement.getKnownRelations();
        for (Individual subject : new HashSet<Individual>(relevantRelations.keySet())) {
            if (isResultRelevantIndividual(subject) && relevantRelations.get(subject).contains(object))
                result.add(subject);
        }
        relevantRelations=representativeElement.getPossibleRelations();
        for (Individual possibleSubject : new HashSet<Individual>(relevantRelations.keySet())) {
            if (isResultRelevantIndividual(possibleSubject) && relevantRelations.get(possibleSubject).contains(object) && isRoleInstance(representativeElement.getRole(),possibleSubject,object)) {
                representativeElement.setToKnown(possibleSubject,object);
                result.add(possibleSubject);
            } else
                for (HierarchyNode<RoleElement> parent : node.getParentNodes())
                    parent.getRepresentative().addPossible(possibleSubject,object);
        }
        for (HierarchyNode<RoleElement> child : node.getChildNodes())
            getObjectPropertySubjects(child, object, result);
    }
    protected void getObjectPropertyValues(HierarchyNode<RoleElement> node, Individual subject, Set<Individual> result) {
        RoleElement representativeElement=node.getRepresentative();
        if (representativeElement.equals(m_topRoleElement) || m_isInconsistent) {
            for (Individual ind : m_individuals)
                if (isResultRelevantIndividual(ind))
                    result.add(ind);
            return;
        } 
        Set<Individual> possibleSuccessors=representativeElement.getPossibleRelations().get(subject);
        if (possibleSuccessors!=null) {
            for (Individual possibleSuccessor : new HashSet<Individual>(possibleSuccessors)) { 
                if (isRoleInstance(representativeElement.getRole(),subject,possibleSuccessor))
                    representativeElement.setToKnown(subject,possibleSuccessor);
                else
                    for (HierarchyNode<RoleElement> parent : node.getParentNodes())
                        parent.getRepresentative().addPossible(subject,possibleSuccessor);
            }
        }
        Set<Individual> knownSuccessors=representativeElement.getKnownRelations().get(subject);
        if (knownSuccessors!=null) {
            for (Individual successor : knownSuccessors) 
                if (isResultRelevantIndividual(successor))
                    result.add(successor);
        }
        for (HierarchyNode<RoleElement> child : node.getChildNodes())
            getObjectPropertyValues(child, subject, result);
    }
    public Set<Individual> getSameAsIndividuals(Individual individual) {
        Set<Individual> equivalenceClass=m_individualToEquivalenceClass.get(individual);
        Set<Set<Individual>> possiblySameEquivalenceClasses=m_individualToPossibleEquivalenceClass.get(equivalenceClass);
        if (possiblySameEquivalenceClasses!=null) {
            while (!possiblySameEquivalenceClasses.isEmpty()) {
                Set<Individual> possiblyEquivalentClass=possiblySameEquivalenceClasses.iterator().next();
                possiblySameEquivalenceClasses.remove(possiblyEquivalentClass);
                if (isSameIndividual(equivalenceClass.iterator().next(), possiblyEquivalentClass.iterator().next())) {
                    if (m_individualToPossibleEquivalenceClass.containsKey(possiblyEquivalentClass)) {
                        possiblySameEquivalenceClasses.addAll(m_individualToPossibleEquivalenceClass.get(possiblyEquivalentClass));
                        m_individualToPossibleEquivalenceClass.remove(possiblyEquivalentClass);
                        for (Individual nowKnownEquivalent : possiblyEquivalentClass)
                            m_individualToEquivalenceClass.put(nowKnownEquivalent, equivalenceClass);
                        equivalenceClass.addAll(possiblyEquivalentClass);
                    }
                } else {
                    Set<Set<Individual>> possiblyEquivalentToNowKnownInequivalent=m_individualToPossibleEquivalenceClass.get(possiblyEquivalentClass);
                    if (possiblyEquivalentToNowKnownInequivalent!=null && possiblyEquivalentToNowKnownInequivalent.contains(equivalenceClass)) {
                        possiblyEquivalentToNowKnownInequivalent.remove(equivalenceClass);
                        if (possiblyEquivalentToNowKnownInequivalent.isEmpty())
                            m_individualToPossibleEquivalenceClass.remove(possiblyEquivalentClass);
                    }
                }
            }
        }
        for (Set<Individual> otherEquivalenceClass : new HashSet<Set<Individual>>(m_individualToPossibleEquivalenceClass.keySet())) {
            if (otherEquivalenceClass!=equivalenceClass && m_individualToPossibleEquivalenceClass.get(otherEquivalenceClass).contains(equivalenceClass)) {
                if (isSameIndividual(equivalenceClass.iterator().next(), otherEquivalenceClass.iterator().next())) {
                    m_individualToPossibleEquivalenceClass.get(otherEquivalenceClass).remove(equivalenceClass);
                    if (m_individualToPossibleEquivalenceClass.get(otherEquivalenceClass).isEmpty())
                        m_individualToPossibleEquivalenceClass.remove(otherEquivalenceClass);
                    for (Individual nowKnownEquivalent : otherEquivalenceClass)
                        m_individualToEquivalenceClass.put(nowKnownEquivalent, equivalenceClass);
                    equivalenceClass.addAll(otherEquivalenceClass);
                }
            }
        }
        return equivalenceClass;
    }
    public boolean isSameIndividual(Individual individual1, Individual individual2) {
        return (!m_tableau.isSatisfiable(true,true,Collections.singleton(Atom.create(Inequality.INSTANCE,individual1,individual2)),null,null,null,null,new ReasoningTaskDescription(true,"is {0} same as {1}",individual1,individual2)));
    }
    public void computeSameAsEquivalenceClasses(ReasonerProgressMonitor progressMonitor) {
        int steps=m_individualToPossibleEquivalenceClass.keySet().size();
        if (steps>0 && progressMonitor!=null)
            progressMonitor.reasonerTaskStarted("Precompute same individuals");
        while (!m_individualToPossibleEquivalenceClass.isEmpty()) {
            Set<Individual> equivalenceClass=m_individualToPossibleEquivalenceClass.keySet().iterator().next();
            getSameAsIndividuals(equivalenceClass.iterator().next());
            if (progressMonitor!=null)
                progressMonitor.reasonerTaskProgressChanged(steps-m_individualToPossibleEquivalenceClass.keySet().size(), steps);
        }
        if (progressMonitor!=null)
            progressMonitor.reasonerTaskStopped();
    }
    protected boolean isInstance(Individual individual,AtomicConcept atomicConcept) {
        return !m_tableau.isSatisfiable(true,true,null,Collections.singleton(Atom.create(atomicConcept,individual)),null,null,null,ReasoningTaskDescription.isInstanceOf(atomicConcept,individual));
    }
    protected boolean isRoleInstance(Role role, Individual individual1, Individual individual2) {
        OWLDataFactory factory=m_reasoner.getDataFactory();
        AtomicRole atomicRole;
        if (role instanceof InverseRole) {
            Individual tmp=individual1;
            individual1=individual2;
            individual2=tmp;
            atomicRole=((InverseRole)role).getInverseOf();
        } else 
            atomicRole=(AtomicRole)role;
        OWLObjectProperty property=factory.getOWLObjectProperty(IRI.create(atomicRole.getIRI()));
        OWLNamedIndividual namedIndividual1=factory.getOWLNamedIndividual(IRI.create(individual1.getIRI()));
        OWLNamedIndividual namedIndividual2=factory.getOWLNamedIndividual(IRI.create(individual2.getIRI()));
        OWLClass pseudoNominal=factory.getOWLClass(IRI.create("internal:pseudo-nominal"));
        OWLClassExpression allNotPseudoNominal=factory.getOWLObjectAllValuesFrom(property,pseudoNominal.getObjectComplementOf());
        OWLAxiom allNotPseudoNominalAssertion=factory.getOWLClassAssertionAxiom(allNotPseudoNominal,namedIndividual1);
        OWLAxiom pseudoNominalAssertion=factory.getOWLClassAssertionAxiom(pseudoNominal,namedIndividual2);
        Tableau tableau=m_reasoner.getTableau(allNotPseudoNominalAssertion,pseudoNominalAssertion);
        return !tableau.isSatisfiable(true,true,null,null,null,null,null,new ReasoningTaskDescription(true,"is {0} connected to {1} via {2}",individual1,individual2,atomicRole));
    }
    protected static boolean isResultRelevantIndividual(Individual individual) {
        return !individual.isAnonymous() && !Prefixes.isInternalIRI(individual.getIRI());
    }
    public boolean realizationCompleted() {
        return m_realizationCompleted;
    }
    public boolean objectPropertyRealizationCompleted() {
        return m_roleRealizationCompleted;
    }
    public boolean sameAsIndividualsComputed() {
        return m_individualToPossibleEquivalenceClass.isEmpty();
    }
    public boolean areClassesInitialised() {
        return m_classesInitialised;
    }
    public boolean arePropertiesInitialised() {
        return m_propertiesInitialised;
    }
    public int getCurrentIndividualIndex() {
        return m_currentIndividualIndex;
    }
    public Map<Individual, Node> getNodesForIndividuals() {
        return m_nodesForIndividuals;
    }
    public HierarchyNode<AtomicConcept> getTopConceptNode() {
        return m_currentConceptHierarchy.getTopNode();
    }
    public HierarchyNode<AtomicConcept> getBottomConceptNode() {
        return m_currentConceptHierarchy.getBottomNode();
    }
    public RoleElement getTopRoleElement() {
        return m_topRoleElement;
    }
    public RoleElement getBottomRoleElement() {
        return m_bottomRoleElement;
    }
    public HierarchyNode<RoleElement> getTopRoleNode() {
        return m_currentRoleHierarchy.getTopNode();
    }
    public HierarchyNode<RoleElement> getBottomRoleNode() {
        return m_currentRoleHierarchy.getBottomNode();
    }
}
