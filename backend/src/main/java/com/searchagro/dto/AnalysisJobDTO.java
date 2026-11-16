package com.searchagro.dto;

public class AnalysisJobDTO {

    private String jobId;
    private String status;
    private int percentage;
    private String task;
    private String tool;
    private String reason;
    private AnaliseUmidadeResponseDTO result;
    private String error;

    public AnalysisJobDTO() {
    }

    public AnalysisJobDTO(
            String jobId,
            String status,
            int percentage,
            String task,
            String tool,
            String reason,
            AnaliseUmidadeResponseDTO result,
            String error) {
        this.jobId = jobId;
        this.status = status;
        this.percentage = percentage;
        this.task = task;
        this.tool = tool;
        this.reason = reason;
        this.result = result;
        this.error = error;
    }

    public String getJobId() {
        return jobId;
    }

    public String getStatus() {
        return status;
    }

    public int getPercentage() {
        return percentage;
    }

    public String getTask() {
        return task;
    }

    public String getTool() {
        return tool;
    }

    public String getReason() {
        return reason;
    }

    public AnaliseUmidadeResponseDTO getResult() {
        return result;
    }

    public String getError() {
        return error;
    }
}
