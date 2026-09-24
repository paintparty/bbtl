# bbtl

Run `bbtl` from a project's root to discover and run [`bb.edn` tasks](https://book.babashka.org/#tasks).


<p align="center">
  <img src="resources/screens/motion/bbtl_light-mode.gif#gh-light-mode-only"></img>
  <img src="resources/screens/motion/bbtl_dark-mode.gif#gh-dark-mode-only"></img>
</p>


<br>

## Requirements

[Babashka](https://babashka.org/) and [bbin](https://github.com/babashka/bbin).

<br>

## Installation
First, install [bbin](https://github.com/babashka/bbin) if it is not on your system.

Then use `bbin` to install `bbtl`:

```sh
bbin install io.github.paintparty/bbtl
bbin uninstall bbtl
```

From the a local checkout of `bbtl`:

```sh
bbin install . --as bbtl-local
bbin uninstall bbtl-local

# Optional default-name local install
bbin install . --as bbtl
```

<br>

## Usage

From any project containing `bb.edn`:

```sh
bbtl
```

The picker preserves public task order from `bb.edn`, ignores private task
names beginning with `-`, and never evaluates task forms during discovery.

Keyboard controls:

- Up/down arrows or `k`/`j`: move the selection.
- Enter: restore the terminal and run the selected task with inherited I/O.
- Escape: cancel with exit status 0.
- Ctrl-C: cancel with exit status 130.


<br>

## Errors and exit statuses

- Missing Babashka, missing or malformed `bb.edn`, and child-process start
  failures return 1 and print diagnostics to stderr.
- A missing `:tasks` map, empty task map, or all-private task map reports that
  no public tasks were found and returns 0.
- A selected task returns the exit status from `bb run <task>`.

`-h`/`--help`, `version`, `-version`, and `--version` print information and
return 0. Unsupported arguments print usage to stderr and return 1.

<br>

## Differences from bb's `<Tab>` completion

Shortly after I finished the initial prototype of this project, Babashka itself announced native support for `<Tab>` completion of tasks in a project's `bb.edn`. Current differences:
- `bb` requires setting up a babashka completions snippet after `compinit` in your `.zshrc` or similar.
- `bb` uses `<Tab>` key for navigation (`bbtl` uses up/down or j/k).
- `bb` lists all tasks, then other files and folders below that. `bbtl` only lists the tasks.
- `bb` lists all tasks alphabetically, `bbtl` preserves order as written.
- `bb` truncates task description after first sentence. `bbtl` will print the whole description, wrapping the second column at 80 columns while preserving the overall 2-column layout.
- Styling of focused task and descriptions: `bb` uses inverse styling on the focused task and description (`bbtl` uses bold, with a `>` char to the left of the focused task).

<br>

## Development

```sh
bb bbtl                 # run the local command implementation
bb test                 # run the tests on the JVM
bb test:bb              # run the tests under Babashka
bb test:all             # run both test suites
bb ci                   # run the CI pipeline (test:all)
```

Install and uninstall a local checkout with:

```sh
bb install:bbtl-local   # install this checkout as bbtl-local
bb install:bbtl         # install this checkout as bbtl
bb uninstall:bbtl-local # uninstall bbtl-local
bb uninstall:bbtl       # uninstall bbtl
```

## License

Copyright © 2026 Jeremiah Coyle

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0)
