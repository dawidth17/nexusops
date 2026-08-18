#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"

#include <arpa/inet.h>
#include <ifaddrs.h>
#include <netinet/in.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>

static sysprobe_status parse_network_line(
    const char *line,
    sysprobe_network_interface *interface
)
{
    char name[SYSPROBE_NETWORK_NAME_MAX] = {0};

    unsigned long long rx_bytes = 0;
    unsigned long long rx_packets = 0;
    unsigned long long rx_errors = 0;
    unsigned long long rx_dropped = 0;
    unsigned long long rx_fifo = 0;
    unsigned long long rx_frame = 0;
    unsigned long long rx_compressed = 0;
    unsigned long long rx_multicast = 0;

    unsigned long long tx_bytes = 0;
    unsigned long long tx_packets = 0;
    unsigned long long tx_errors = 0;
    unsigned long long tx_dropped = 0;
    unsigned long long tx_fifo = 0;
    unsigned long long tx_collisions = 0;
    unsigned long long tx_carrier = 0;
    unsigned long long tx_compressed = 0;

    int parsed = sscanf(
        line,
        " %63[^:]:"
        " %llu %llu %llu %llu %llu %llu %llu %llu"
        " %llu %llu %llu %llu %llu %llu %llu %llu",
        name,
        &rx_bytes,
        &rx_packets,
        &rx_errors,
        &rx_dropped,
        &rx_fifo,
        &rx_frame,
        &rx_compressed,
        &rx_multicast,
        &tx_bytes,
        &tx_packets,
        &tx_errors,
        &tx_dropped,
        &tx_fifo,
        &tx_collisions,
        &tx_carrier,
        &tx_compressed
    );

    if (parsed != 17) {
        return SYSPROBE_ERROR_PARSE;
    }

    memset(
        interface,
        0,
        sizeof(*interface)
    );

    int written = snprintf(
        interface->name,
        sizeof(interface->name),
        "%s",
        name
    );

    if (
        written < 0 ||
        (size_t) written >=
            sizeof(interface->name)
    ) {
        return SYSPROBE_ERROR_PARSE;
    }

    interface->rx_bytes =
        (uint64_t) rx_bytes;

    interface->rx_packets =
        (uint64_t) rx_packets;

    interface->rx_errors =
        (uint64_t) rx_errors;

    interface->rx_dropped =
        (uint64_t) rx_dropped;

    interface->tx_bytes =
        (uint64_t) tx_bytes;

    interface->tx_packets =
        (uint64_t) tx_packets;

    interface->tx_errors =
        (uint64_t) tx_errors;

    interface->tx_dropped =
        (uint64_t) tx_dropped;

    return SYSPROBE_OK;
}

sysprobe_status sysprobe_read_network_counters_from_path(
    const char *path,
    sysprobe_network_interface *interfaces,
    size_t capacity,
    size_t *count
)
{
    if (
        path == NULL ||
        count == NULL ||
        (interfaces == NULL && capacity != 0)
    ) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    FILE *file =
        fopen(path, "r");

    if (file == NULL) {
        return SYSPROBE_ERROR_IO;
    }

    size_t total = 0;

    char line[1024];

    while (
        fgets(
            line,
            sizeof(line),
            file
        ) != NULL
    ) {
        if (strchr(line, ':') == NULL) {
            continue;
        }

        sysprobe_network_interface current;

        sysprobe_status status =
            parse_network_line(
                line,
                &current
            );

        if (status != SYSPROBE_OK) {
            fclose(file);

            return status;
        }

        if (
            interfaces != NULL &&
            total < capacity
        ) {
            interfaces[total] =
                current;
        }

        ++total;
    }

    if (ferror(file)) {
        fclose(file);

        return SYSPROBE_ERROR_IO;
    }

    fclose(file);

    *count = total;

    if (
        interfaces != NULL &&
        total > capacity
    ) {
        return SYSPROBE_ERROR_BUFFER_TOO_SMALL;
    }

    return SYSPROBE_OK;
}

static sysprobe_network_interface *find_interface(
    sysprobe_network_interface *interfaces,
    size_t count,
    const char *name
)
{
    for (
        size_t index = 0;
        index < count;
        ++index
    ) {
        if (
            strcmp(
                interfaces[index].name,
                name
            ) == 0
        ) {
            return &interfaces[index];
        }
    }

    return NULL;
}

static void add_interface_address(
    sysprobe_network_interface *interface,
    const struct sockaddr *address
)
{
    if (
        interface == NULL ||
        address == NULL
    ) {
        return;
    }

    if (
        address->sa_family == AF_INET &&
        interface->ipv4_address[0] == '\0'
    ) {
        const struct sockaddr_in *ipv4 =
            (const struct sockaddr_in *) address;

        inet_ntop(
            AF_INET,
            &ipv4->sin_addr,
            interface->ipv4_address,
            sizeof(interface->ipv4_address)
        );

        return;
    }

    if (
        address->sa_family == AF_INET6 &&
        interface->ipv6_address[0] == '\0'
    ) {
        const struct sockaddr_in6 *ipv6 =
            (const struct sockaddr_in6 *) address;

        inet_ntop(
            AF_INET6,
            &ipv6->sin6_addr,
            interface->ipv6_address,
            sizeof(interface->ipv6_address)
        );
    }
}

sysprobe_status sysprobe_read_network_interfaces(
    sysprobe_network_interface *interfaces,
    size_t capacity,
    size_t *count
)
{
    sysprobe_status counter_status =
        sysprobe_read_network_counters_from_path(
            "/proc/net/dev",
            interfaces,
            capacity,
            count
        );

    if (
        counter_status != SYSPROBE_OK &&
        counter_status !=
            SYSPROBE_ERROR_BUFFER_TOO_SMALL
    ) {
        return counter_status;
    }

    if (
        interfaces == NULL ||
        capacity == 0
    ) {
        return counter_status;
    }

    struct ifaddrs *addresses = NULL;

    if (getifaddrs(&addresses) != 0) {
        return SYSPROBE_ERROR_IO;
    }

    size_t stored_count =
        *count < capacity
            ? *count
            : capacity;

    for (
        struct ifaddrs *current = addresses;
        current != NULL;
        current = current->ifa_next
    ) {
        if (
            current->ifa_name == NULL ||
            current->ifa_addr == NULL
        ) {
            continue;
        }

        int family =
            current->ifa_addr->sa_family;

        if (
            family != AF_INET &&
            family != AF_INET6
        ) {
            continue;
        }

        sysprobe_network_interface *interface =
            find_interface(
                interfaces,
                stored_count,
                current->ifa_name
            );

        add_interface_address(
            interface,
            current->ifa_addr
        );
    }

    freeifaddrs(addresses);

    return counter_status;
}