package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/bruceasu/pg-eventbus/gateway/internal/api"
	"github.com/bruceasu/pg-eventbus/gateway/internal/config"
	"github.com/bruceasu/pg-eventbus/gateway/internal/store"
)

func run(args []string, out io.Writer) int {
	if len(args) == 1 && (args[0] == "--help" || args[0] == "-h") {
		fmt.Fprintln(out, "pg-eventbus-gateway: independent HTTP gateway (default :8080)")
		fmt.Fprintln(out, "Configure PGEVENTBUS_DATABASE_URL, optional DATABASE_USER/PASSWORD, SCHEMA,")
		fmt.Fprintln(out, "BATCH_SIZE, LOCK_TTL_SECONDS, LISTEN_ADDR, REQUEST_TIMEOUT (all PGEVENTBUS_ prefixed).")
		fmt.Fprintln(out, "See gateway/README.md for legacy environment aliases and migration.")
		return 0
	}
	if len(args) != 0 { fmt.Fprintln(out, "unsupported arguments; use --help"); return 2 }
	c, err := config.Load()
	if err != nil { fmt.Fprintln(out, err); return 2 }
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	startup, cancel := context.WithTimeout(ctx, 10*time.Second)
	database, err := store.Open(startup, c)
	cancel()
	if err != nil { fmt.Fprintln(out, err); return 1 }
	defer func() {
		// Also cancel active queries on unexpected Serve/listen failures, before
		// pool.Close waits for borrowed connections to be returned.
		stop()
		database.Close()
	}()
	listener, err := net.Listen("tcp", c.ListenAddr)
	if err != nil { fmt.Fprintln(out, "could not listen; check listen address and port availability"); return 1 }
	server := &http.Server{
		Handler: api.New(database, c.RequestTimeout),
		ReadHeaderTimeout: 5*time.Second,
		ReadTimeout: 15*time.Second,
		WriteTimeout: c.RequestTimeout + 5*time.Second,
		IdleTimeout: 60*time.Second,
		MaxHeaderBytes: 1 << 20,
		BaseContext: func(net.Listener) context.Context { return ctx },
		// Handler failures are sanitized by api; suppress transport dumps of supplied data.
		ErrorLog: log.New(io.Discard, "", 0),
	}
	finished := make(chan error, 1)
	go func() { finished <- server.Serve(listener) }()
	fmt.Fprintln(out, "pg-eventbus-gateway listening on", c.ListenAddr)
	select {
	case err := <-finished:
		if !errors.Is(err, http.ErrServerClosed) { fmt.Fprintln(out, "HTTP server stopped unexpectedly"); return 1 }
		return 0
	case <-ctx.Done():
		// BaseContext cancels in-flight database work before waiting for HTTP draining.
		shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		err := server.Shutdown(shutdown)
		cancel()
		if err != nil {
			_ = server.Close()
			fmt.Fprintln(out, "HTTP graceful shutdown deadline exceeded; connections closed")
			return 1
		}
		return 0
	}
}

func main() { os.Exit(run(os.Args[1:], os.Stderr)) }
