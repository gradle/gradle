#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <netinet/ip.h>
#include <arpa/inet.h>

void runDaemon(char *const *argv);
void runDirect(char*const* argv);

int main( int argc, char* const * argv)
{
    int daemon = 0;
    if (argc > 1) {
        if (strcmp(argv[1], "--daemon") == 0) {
            daemon = 1;
        }
    }
    if (daemon) {
        runDaemon(argv);
    } else {
        runDirect(argv);
    }
    exit(0);
}

void doWrite(int fd, const char* string) {
    int retval = write(fd, string, strlen(string));
    if (retval < 0) {
        perror("failed to write to daemon");
        exit(1);
    }
}

void runDaemon(char* const *argv) {
    printf("RUNNING DAEMON MODE\n");
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        perror("failed to create socket");
        exit(1);
    }
    sockaddr_in address;
    address.sin_family = AF_INET;
    address.sin_port = htons(23000);
    int retval = inet_aton("127.0.0.1", &address.sin_addr);
    if (retval == 0) {
        perror("failed to convert IP address");
        exit(1);
    }
    retval = connect(fd, (sockaddr*)&address, sizeof(sockaddr_in));
    if (retval != 0) {
        perror("failed to connect to daemon");
        exit(1);
    }

    printf("connected\n");

    char* cwd = getcwd(NULL, 0);
    doWrite(fd, cwd);
    free(cwd);
    doWrite(fd, "\n");

    char* const* current = argv + 2;
    while (*current) {
        doWrite(fd, *current);
        doWrite(fd, "\n");

        current++;
    }
    doWrite(fd, "\n");

    printf("waiting for result\n");

    void* buffer = malloc(1024);
    while(true) {
        ssize_t nread = read(fd, buffer, 1024);
        if (nread < 0) {
            perror("failed to read from daemon");
            exit(1);
        }
        if (nread == 0) {
            break;
        }
        ssize_t nwritten = write(1, buffer, nread);
    }
}

void runDirect(char* const *argv) {
    printf("RUNNING DIRECT MODE\n");
    pid_t pid = fork();
    if (pid < 0) {
        perror("failed to fork child process");
        exit(1);
    }
    if (pid == 0) {
        printf("[child] execing\n");
        const char** args = (const char**)malloc(6 * sizeof(char*));
        args[0] = "java";
        args[1] = "-cp";
        args[2] = "/home/adam/Documents/gradle/current/lib/gradle-launcher-1.0-milestone-7-20111207112830+1100.jar";
        args[3] = "org.gradle.launcher.GradleMain";
        args[4] = "help";
        args[5] = NULL;
        int retval = execv("/home/adam/jdk1.6.0_22/bin/java", (char* const *)args);
        if (retval < 0) {
            perror("failed to exec gradle");
            exit(1);
        }
        free(args);
    } else {
        printf("[parent] waiting.\n");
        int siginfo;
        pid_t retval = waitpid(pid, &siginfo, 0);
        if (retval < 0) {
            perror("failed to wait for child process");
            exit(1);
        }
        printf("[parent] child finished.\n");
    }
}
