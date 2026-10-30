package co.wethinkcode.healthsafe;

public record Schedule(String[] doctors, int alertLevel, String department, String wardId) {
}
