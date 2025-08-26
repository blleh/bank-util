package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

public class FileMapper {

    // Record to hold GitLab stats
    record GitLabStats(String login, int linesAdded, int linesDeleted) {}

    // Record to hold employee data
    record Employee(String id, String name) {}

    public static void main(String[] args) throws IOException {
        // Paths to files
        Path namesFilePath = Path.of("names.txt");
        Path commitsFilePath = Path.of("commits.csv");
        Path mrsFilePath = Path.of("mrs.csv");
        Path jiraFilePath = Path.of("jira.csv");

        // Read names into a list of Employee records
        List<Employee> employees = Files.lines(namesFilePath)
                .map(line -> line.split(",", 2)) // Split by tab
                .map(parts -> new Employee(parts[0], normalize(parts[1]))) // Employee ID and normalized name
                .toList();

        // Read GitLab stats into a map (login/email -> GitLabStats)
        List<GitLabStats> gitlabStats = Files.lines(commitsFilePath)
                .skip(1) // Skip header
                .map(line -> line.split(","))
                .map(fields -> new GitLabStats(fields[0], Integer.parseInt(fields[1]), Integer.parseInt(fields[2])))
                .toList();

        // Read merge requests into a map (login/email -> merge requests count)
        Map<String, Integer> mergeRequests = Files.lines(mrsFilePath)
                .skip(1) // Skip header
                .map(line -> line.split(","))
                .collect(Collectors.toMap(
                        fields -> fields[0], // login/email as key
                        fields -> Integer.parseInt(fields[1])));

        // Read Jira data into a map (Employee ID -> number of JIRA tickets)
        Map<String, Integer> jiraTickets = Files.lines(jiraFilePath)
                .skip(1) // Skip header
                .map(line -> line.split(";"))
                .collect(Collectors.toMap(
                        fields -> fields[1], // Employee ID as key
                        fields -> Integer.parseInt(fields[6]))); // Number of JIRA tickets

        // Generate output for each employee in the order of the names file
        List<String> output = employees.stream()
                .map(employee -> {
                    String employeeId = employee.id();
                    String name = employee.name();

                    // Find GitLab stats where login contains the name
                    Optional<GitLabStats> statsOpt = gitlabStats.stream()
                            .filter(stats -> normalize(stats.login()).toLowerCase().contains(name.toLowerCase()))
                            .findFirst();

                    // Find merge requests where login contains the name
                    Optional<Map.Entry<String, Integer>> mergeReqOpt = mergeRequests.entrySet().stream()
                            .filter(entrySet -> normalize(entrySet.getKey()).toLowerCase().contains(name.toLowerCase()))
                            .findFirst();

                    // Find Jira tickets by employee ID
                    Integer jiraCount = jiraTickets.getOrDefault(employeeId, 0);

                    GitLabStats stats = statsOpt.orElse(null);
                    Integer mergeRequestCount = mergeReqOpt.map(Map.Entry::getValue).orElse(null);

                    String jiraPart = "JIRA tickets: " + jiraCount;
                    String mergePart = "Merge requests submitted: " + (mergeRequestCount == null ? 0 : mergeRequestCount);
                    String statsPart = "Lines of code created: " + (stats == null ? 0 : stats.linesAdded) + ", deleted: " + (stats == null ? 0 : stats.linesDeleted);

                    return name + ": " + mergePart + ", " + statsPart + ", " + jiraPart;
                })
                .collect(Collectors.toList());

        // Write output to a file
        Path outputFilePath = Path.of("output.txt");
        Files.write(outputFilePath, output);

        System.out.println("Output written to " + outputFilePath);
    }

    // Normalize method to remove Polish characters and replace them with English equivalents
    private static String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("ł", "l") // Specific handling for Polish characters
                .replaceAll("Ł", "L");
    }
}
