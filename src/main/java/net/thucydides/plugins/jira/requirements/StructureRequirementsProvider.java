package net.thucydides.plugins.jira.requirements;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.model.TestOutcome;
import net.thucydides.core.model.TestTag;
import net.thucydides.core.requirements.RequirementsTagProvider;
import net.thucydides.core.requirements.model.Requirement;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.plugins.jira.client.JerseyJiraClient;
import net.thucydides.plugins.jira.domain.IssueSummary;
import net.thucydides.plugins.jira.service.JIRAConfiguration;
import net.thucydides.plugins.jira.service.SystemPropertiesJIRAConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;
import java.util.Set;


/**
 * Integrate Thucydides reports with requirements, epics and stories in a JIRA server.
 */
public class StructureRequirementsProvider implements RequirementsTagProvider {

    private List<Requirement> requirements = null;
    private final JerseyJiraClient jiraClient;
    private final String projectKey;
    private final int providedStructureId;

    private List<String> requirementsLinks = ImmutableList.of("Epic Link");
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(StructureRequirementsProvider.class);

    public StructureRequirementsProvider() {
        this(Injectors.getInjector().getInstance(EnvironmentVariables.class));
    }

    public StructureRequirementsProvider(EnvironmentVariables environmentVariables) {
        JIRAConfiguration jiraConfiguration = new SystemPropertiesJIRAConfiguration(environmentVariables);
        logConnectionDetailsFor(jiraConfiguration);
        jiraClient = new JerseyJiraClient(jiraConfiguration.getJiraUrl(),
                                          jiraConfiguration.getJiraUser(),
                                          jiraConfiguration.getJiraPassword());
        this.projectKey = jiraConfiguration.getProject();
        this.providedStructureId = environmentVariables.getPropertyAsInteger("structure.id",0);
    }

    private void logConnectionDetailsFor(JIRAConfiguration jiraConfiguration) {
        logger.debug("JIRA URL: {0}", jiraConfiguration.getJiraUrl());
        logger.debug("JIRA project: {0}", jiraConfiguration.getProject());
        logger.debug("JIRA user: {0}", jiraConfiguration.getJiraUser());
    }

    private String getProjectKey() {
        return projectKey;
    }

    @Override
    public List<Requirement> getRequirements() {
        if (requirements == null) {
            if (providedStructureId != 0) {
                requirements = getGetRequirementsForStructure(providedStructureId);
            } else {
                List<Integer> structureIds = getStructureIds();
                requirements = getGetRequirementsForStructure(structureIds.get(0));
            }
        }
        return requirements;
    }

    public List<Integer> getStructureIds() {
        List<Integer> ids = Lists.newArrayList();
        try {
            WebTarget target = jiraClient.buildWebTargetFor("rest/structure/1.0/structure");
            Response response = target.request().get();
            jiraClient.checkValid(response);
            String jsonResponse = response.readEntity(String.class);
            JSONObject responseObject = new JSONObject(jsonResponse);
            JSONArray structureEntries = (JSONArray) responseObject.getJSONArray("structures");
            for (int i = 0; i < structureEntries.length(); i++) {
                JSONObject structure = structureEntries.getJSONObject(i);
                ids.add(getIdFrom(structure));
            }
        } catch(JSONException e) {
            throw new IllegalArgumentException("Failed to load structure data", e);
        }
        return ids;
    }

    public Integer getIdFrom(JSONObject structure) {
        try {
            return Integer.parseInt(stringValueOf(structure.get("id")));
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

     String stringValueOf(Object field) {
        if (field != null) {
            return field.toString();
        } else {
            return null;
        }
    }

    private Requirement requirementFrom(IssueSummary issue) {
        return Requirement.named(issue.getSummary())
                .withOptionalCardNumber(issue.getKey())
                .withType(issue.getType())
                .withNarrativeText(issue.getDescription());
    }

    @Override
    public Optional<Requirement> getParentRequirementOf(TestOutcome testOutcome) {
        List<String> issueKeys = testOutcome.getIssueKeys();
        if (!issueKeys.isEmpty()) {
            try {
                List<IssueSummary> parentIssues = jiraClient.findByJQL("key=" + issueKeys.get(0));
                return Optional.of(requirementFrom(parentIssues.get(0)));
            } catch (JSONException e) {
                if (noSuchIssue(e)) {
                    return Optional.absent();
                } else {
                    throw new IllegalArgumentException(e);
                }
            }
        } else {
            return Optional.absent();
        }
    }

    @Override
    public Optional<Requirement> getRequirementFor(TestTag testTag) {
        for (Requirement requirement : getFlattenedRequirements()) {
            if (requirement.getType().equals(testTag.getType()) && requirement.getName().equals(testTag.getName())) {
                return Optional.of(requirement);
            }
        }
        return Optional.absent();
    }

    public Optional<Requirement> getParentRequirementOf(String key) {
        for (Requirement requirement : getFlattenedRequirements()) {
            if (containsRequirementWithId(key, requirement.getChildren())) {
                return Optional.of(requirement);
            }
        }
        return Optional.absent();
    }

    private boolean containsRequirementWithId(String key, List<Requirement> requirements) {
        for(Requirement requirement : requirements) {
            if (requirement.getCardNumber().equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<TestTag> getTagsFor(TestOutcome testOutcome) {
        List<String> issues  = testOutcome.getIssueKeys();
        Set<TestTag> tags = Sets.newHashSet();
        for(String issue : issues) {
            tags.addAll(tagsFromIssue(issue));
        }
        return ImmutableSet.copyOf(tags);
    }

    private Collection<? extends TestTag> tagsFromIssue(String issueKey) {

        System.out.println("Reading tags from issue " + issueKey);
        List<TestTag> tags = Lists.newArrayList();

        String decodedIssueKey = decoded(issueKey);
        List<IssueSummary> behaviourIssues = Lists.newArrayList();
        try {
            behaviourIssues = jiraClient.findByJQL("key=" + decodedIssueKey);
        } catch (JSONException e) {
            if (!noSuchIssue(e)) {
                throw new IllegalArgumentException(e);
            }
        }
        if (!behaviourIssues.isEmpty()) {
            IssueSummary behaviourIssue = behaviourIssues.get(0);
            tags.add(TestTag.withName(behaviourIssue.getSummary()).andType(behaviourIssue.getType()));
        }

        List<Requirement> parentRequirements = getParentRequirementsOf(decodedIssueKey);
        for(Requirement parentRequirement : parentRequirements) {
            TestTag parentTag = TestTag.withName(parentRequirement.getName())
                                       .andType(parentRequirement.getType());
            tags.add(parentTag);
        }
        return tags;
    }

    private boolean noSuchIssue(JSONException e) {
        return e.getMessage().contains("error 400");
    }

    private List<Requirement> getParentRequirementsOf(String issueKey) {
        List<Requirement> parentRequirements = Lists.newArrayList();

        Optional<Requirement> parentRequirement = getParentRequirementOf(issueKey);
        if (parentRequirement.isPresent()) {
            parentRequirements.add(parentRequirement.get());
            parentRequirements.addAll(getParentRequirementsOf(parentRequirement.get().getCardNumber()));
        }

        return parentRequirements;
    }

    private String decoded(String issueKey) {
        if (issueKey.startsWith("#")) {
            issueKey = issueKey.substring(1);
        }
        if (StringUtils.isNumeric(issueKey)) {
            issueKey = getProjectKey() + "-" + issueKey;
        }
        return issueKey;
    }

    private List<Requirement> getFlattenedRequirements(){
        return getFlattenedRequirements(getRequirements());
    }

    private List<Requirement> getFlattenedRequirements(List<Requirement> someRequirements){
        List<Requirement> flattenedRequirements = Lists.newArrayList();
        for (Requirement requirement : someRequirements) {
            flattenedRequirements.add(requirement);
            flattenedRequirements.addAll(getFlattenedRequirements(requirement.getChildren()));
        }
        return flattenedRequirements;
    }

    public String getForestForStructure(int structureId) throws JSONException {
        WebTarget target = jiraClient.buildWebTargetFor("rest/structure/1.0/structure/" + structureId + "/forest");
        Response response = target.request().get();
        jiraClient.checkValid(response);

        String jsonResponse = response.readEntity(String.class);
        JSONObject responseObject = new JSONObject(jsonResponse);
        return stringValueOf(responseObject.get("formula"));
    }

    public List<Requirement> getGetRequirementsForStructure(int structureId) {
        try {
            String forest = getForestForStructure(structureId);
            StructureRequirementsTree tree = StructureRequirementsTree.forFormula(forest);
            return loadRequirements(tree.getNodes());
        } catch(JSONException e) {
            throw new IllegalStateException("Failed to load structure requirement",e);
        }
    }

    private List<Requirement> loadRequirements(List<StructureRequirementsTree.RequirementTreeNode> nodes) throws JSONException {
        List<Requirement> requirements = Lists.newArrayList();
        for(StructureRequirementsTree.RequirementTreeNode node : nodes) {
            List<Requirement> children = loadRequirements(node.getChildren());
            Requirement currentRequirement = loadRequirementById(node.id).withChildren(children);
            requirements.add(currentRequirement);
        }
        return requirements;
    }

    private Requirement loadRequirementById(int id) throws JSONException {
        IssueSummary issue = jiraClient.findByKey(Integer.toString(id));
        return requirementFrom(issue);
    }

}
