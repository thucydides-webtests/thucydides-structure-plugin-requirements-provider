package net.thucydides.plugins.jira

import net.thucydides.plugins.jira.requirements.StructureRequirementsTree
import spock.lang.Specification

class WhenParsingTheStructureFormula extends Specification {

    def "should read empty structure tree"() {
        when:
            StructureRequirementsTree tree = StructureRequirementsTree.forFormula("")
        then:
            tree.nodes.size() == 0
    }

    def "should read structure tree with one feature"() {
        when:
            StructureRequirementsTree tree = StructureRequirementsTree.forFormula("1000:0")
        then:
            tree.nodes.size() == 1
        and:
            tree.nodes.get(0).id == 1000
    }

    def "should read structure tree with two features"() {
        when:
            StructureRequirementsTree tree = StructureRequirementsTree.forFormula("1000:0,1001:0")
        then:
            tree.nodes.size() == 2
        and:
            tree.nodes.collect { it.id } == [1000,1001]
    }

    def "should read structure tree with a feature with a child"() {
        when:
            StructureRequirementsTree tree = StructureRequirementsTree.forFormula("1000:0,1001:1")
        then:
            tree.nodes.size() == 1
        and:
            tree.nodes[0].id == 1000
        and:
            tree.nodes[0].children.size() == 1
        and:
            tree.nodes[0].children[0].id == 1001
    }

    def "should read structure tree with a feature with children"() {
        when:
        StructureRequirementsTree tree = StructureRequirementsTree.forFormula("1000:0,1001:1,1002:1")
        then:
            tree.nodes.size() == 1
        and:
            tree.nodes[0].id == 1000
        and:
            tree.nodes[0].children.size() == 2
        and:
            tree.nodes[0].children[0].id == 1001
            tree.nodes[0].children[1].id == 1002
    }

    def "should read structure tree with a feature with nested children"() {
        when:
            StructureRequirementsTree tree = StructureRequirementsTree.forFormula("1000:0,1001:1,1002:2")
        then:
            tree.nodes.size() == 1
        and:
            tree.nodes[0].id == 1000
        and:
            tree.nodes[0].children.size() == 1
        and:
            tree.nodes[0].children[0].id == 1001
            tree.nodes[0].children[0].children.size() == 1
            tree.nodes[0].children[0].children[0].id == 1002
    }

    def "should read structure tree with several features with nested children"() {
        when:
            StructureRequirementsTree tree = StructureRequirementsTree.forFormula("1000:0,1001:1,1002:2,1003:0,1004:1")
        then:
            tree.nodes.size() == 2
        and:
            tree.nodes[0].id == 1000
            tree.nodes[1].id == 1003
        and:
            tree.nodes[0].children.size() == 1
        and:
            tree.nodes[0].children[0].id == 1001
            tree.nodes[0].children[0].children.size() == 1
            tree.nodes[0].children[0].children[0].id == 1002
        and:
            tree.nodes[1].children.size() == 1
        and:
            tree.nodes[1].children[0].id == 1004
    }

    def "should read structure tree with several features with deeply nested children"() {
        when:
        StructureRequirementsTree tree = StructureRequirementsTree.forFormula("1000:0,1001:1,1002:2,1003:2,1004:1,1005:2")
        then:
        tree.nodes.size() == 1
        and:
        tree.nodes[0].id == 1000
        and:
        tree.nodes[0].children.size() == 2
        and:
        tree.nodes[0].children[0].id == 1001
        tree.nodes[0].children[1].id == 1004
    }
}
