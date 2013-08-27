package net.thucydides.plugins.jira

import net.thucydides.core.model.TestOutcome
import net.thucydides.core.model.TestTag
import net.thucydides.core.requirements.model.Requirement
import net.thucydides.core.util.MockEnvironmentVariables
import net.thucydides.plugins.jira.requirements.StructureRequirementsProvider
import spock.lang.Specification

class WhenReadingStoriesFromJira extends Specification {


    def environmentVariables = new MockEnvironmentVariables()
    def requirementsProvider

    def setup() {
        environmentVariables.setProperty('jira.url','http://54.253.114.157:8082')
        environmentVariables.setProperty('jira.username','john.smart')
        environmentVariables.setProperty('jira.password','arv-uf-gis-bo-gl')
        environmentVariables.setProperty('jira.project','PAV')
        requirementsProvider = new StructureRequirementsProvider(environmentVariables)
    }

    def "should read features as the top level requirements by default"() {
        when:
            List<Requirement> requirements = requirementsProvider.getRequirements()
        then:
            !requirements.isEmpty()
        and:
            requirements.each { requirement -> requirement.type == 'Feature' }
    }

    def "should read stories beneath the features"() {
        given:
            def requirementsProvider = new StructureRequirementsProvider(environmentVariables)
        when:
            List<Requirement> requirements = requirementsProvider.getRequirements()
        then:
            requirements.find {epic -> !epic.children.isEmpty() }
    }

    def "should read the list of available structures"() {
        given:
            StructureRequirementsProvider requirementsProvider = new StructureRequirementsProvider(environmentVariables)
        when:
            List<Integer> structureIds = requirementsProvider.getStructureIds()
        then:
            structureIds == [100,1]
    }

    def "should get the forest structure for a given structure id"() {
        given:
            StructureRequirementsProvider requirementsProvider = new StructureRequirementsProvider(environmentVariables)
        when:
            def forest = requirementsProvider.getForestForStructure(100)
        then:
            forest == "10017:0,10021:1,10022:1,10018:0,10023:1,10024:1,10019:0,10020:0"
    }

    def "should get the forest structure for a structure id specified in a system property"() {
        given:
            environmentVariables.setProperty("structure.id","1")
            StructureRequirementsProvider requirementsProvider = new StructureRequirementsProvider(environmentVariables)
        when:
            //10008:0,10011:1,10012:1,10013:1,10009:0,10014:1,10015:1,10010:0,10016:1
            def requirements = requirementsProvider.getRequirements()
        then:
            requirements.size() == 3
        and:
            requirements.get(0).cardNumber == "PAV-2"
    }


    def "should get the requirements structure for a given structure id"() {
        given:
            StructureRequirementsProvider requirementsProvider = new StructureRequirementsProvider(environmentVariables)
        when:
            def requirements = requirementsProvider.getGetRequirementsForStructure(100)
        then:
            requirements.size() == 4
    }

    def "should get the requirements structure for the first structure by default"() {
        given:
            StructureRequirementsProvider requirementsProvider = new StructureRequirementsProvider(environmentVariables)
        when:
            def requirements = requirementsProvider.getRequirements();
        then:
            requirements.size() == 4
    }

    def "should find the parent requirement from a given issue"() {
        given:
            def requirementsProvider = new StructureRequirementsProvider(environmentVariables)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["PAV-15"]
        when:
            def parentRequirement = requirementsProvider.getParentRequirementOf(testOutcome)
        then:
            parentRequirement.isPresent() && parentRequirement.get().cardNumber == "PAV-15"
    }

    def "should not find the parent requirement from a given issue if none exist"() {
        given:
            def requirementsProvider = new StructureRequirementsProvider(environmentVariables)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["PAV-99999"]
        when:
            def parentRequirement = requirementsProvider.getParentRequirementOf(testOutcome)
        then:
            !parentRequirement.isPresent()
    }


    def "should find tags for a given issue"() {
        given:
            def requirementsProvider = new StructureRequirementsProvider(environmentVariables)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["PAV-15"]
        when:
            def tags = requirementsProvider.getTagsFor(testOutcome)
        then:
            tags.contains(TestTag.withName("Transmission dashboard").andType("Story")) &&
            tags.contains(TestTag.withName("Monitoring a Transmission").andType("New Feature"))
    }

    def "should find tags for a given issue from the ID number"() {
        given:
            def requirementsProvider = new StructureRequirementsProvider(environmentVariables)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["15"]
        when:
            def tags = requirementsProvider.getTagsFor(testOutcome)
        then:
            tags.contains(TestTag.withName("Transmission dashboard").andType("Story")) &&
            tags.contains(TestTag.withName("Monitoring a Transmission").andType("New Feature"))
        }

    def "should find tags for a given issue from the hashed ID number"() {
        given:
            def requirementsProvider = new StructureRequirementsProvider(environmentVariables)
            def testOutcome = Mock(TestOutcome)
            testOutcome.getIssueKeys() >> ["#15"]
        when:
            def tags = requirementsProvider.getTagsFor(testOutcome)
        then:
            tags.contains(TestTag.withName("Transmission dashboard").andType("Story")) &&
            tags.contains(TestTag.withName("Monitoring a Transmission").andType("New Feature"))
    }

    def "should find a requirement with a given tag"() {
        given:
            def requirementsProvider = new StructureRequirementsProvider(environmentVariables)
        when:
            def requirement = requirementsProvider.getRequirementFor(TestTag.withName("Transmission dashboard").andType("Story"))
        then:
            requirement.isPresent()
        and:
            requirement.get().cardNumber == "PAV-15"
    }

    def "should not find a requirement with a given tag if none match"() {
        given:
            def requirementsProvider = new StructureRequirementsProvider(environmentVariables)
        when:
            def requirement = requirementsProvider.getRequirementFor(TestTag.withName("Transmission dashboard").andType("Bug"))
        then:
            !requirement.isPresent()
    }

}
