var int total = 0;

fun void add(int value) {
    total = total + value;
}

fun int main() {
    add(2);
    add(3);
    printf("%d\n", total);
    return 0;
}
