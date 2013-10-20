package net.thucydides.plugins.jira.requirements;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class StructureRequirementsTree {

    public static class RequirementTreeNode {
        final Integer id;
        List<RequirementTreeNode> children;

        public RequirementTreeNode(Integer id) {
            this.id = id;
            this.children = Lists.newArrayList();
        }

        public void addChild(RequirementTreeNode child) {
            children.add(child);
        }

        public Integer getId() {
            return id;
        }

        public List<RequirementTreeNode> getChildren() {
            return ImmutableList.copyOf(children);
        }
    }

    public static StructureRequirementsTree forFormula(String formula) {
        List<String> leaves = Lists.newArrayList(Splitter.on(",").omitEmptyStrings().split(formula));

        List<RequirementTreeNode> nodes = Lists.newArrayList();
        Stack<RequirementTreeNode> nodeStack = new Stack<RequirementTreeNode>();
        RequirementTreeNode currentNode = null;

        int currentLevel = 0;
        for(String leaf : leaves) {
            int id = getIdOf(leaf);
            int level = getLevelOf(leaf);
            RequirementTreeNode node = new RequirementTreeNode(id);
            if (top(level)) {
                nodes.add(node);
                currentNode = node;
                currentLevel = level;
            } else if (level > currentLevel) {
                nodeStack.push(currentNode);
                currentNode.addChild(node);
                currentNode = node;
                currentLevel = level;
            } else if (level == currentLevel) {
                nodeStack.peek().addChild(node);
            } else if (level < currentLevel) {
                nodeStack.pop();
                nodeStack.peek().addChild(node);
                currentNode = node;
                currentLevel = level;
            }
        }

        return new StructureRequirementsTree(nodes);
    }

    private static boolean top(int level) {
        return level == 0;
    }

    private List<RequirementTreeNode> nodes;

    public StructureRequirementsTree(List<RequirementTreeNode> nodes) {
        this.nodes = nodes;
    }

    public List<RequirementTreeNode> getNodes() {
        return ImmutableList.copyOf(nodes);
    }

    private static Integer getIdOf(String leaf) {
        int separator = leaf.indexOf(":");
        return Integer.valueOf(leaf.substring(0, separator));
    }

    private static Integer getLevelOf(String leaf) {
        int separator = leaf.indexOf(":");
        return Integer.valueOf(leaf.substring(separator + 1));
    }

}
