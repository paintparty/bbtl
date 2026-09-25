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
If not already installed, install [bbin](https://github.com/babashka/bbin).

Then use `bbin` to install `bbtl`:

```sh
bbin install io.github.paintparty/bbtl
```

To uninstall:
```sh
bbin uninstall bbtl
```

<br>

## Usage

From any project containing a `bb.edn`:

```sh
bbtl
```

The picker preserves the order of (public) tasks from `bb.edn`, ignores private task
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

Shortly after the initial prototype of `bbtl` was complete, Babashka itself announced native support for `<Tab>` completion of tasks in a project's `bb.edn`. Current differences as compared to `bbtl`:
- `bb` requires setting up a babashka completions snippet after `compinit` in your `.zshrc` or similar.
- `bb` uses `<Tab>` key for navigation (`bbtl` uses up/down or j/k).
- `bb` lists all tasks, then other files and folders below that. `bbtl` only lists the tasks.
- `bb` lists all tasks alphabetically, `bbtl` preserves order as written.
- `bb` truncates task description after first sentence. `bbtl` will print the whole description, wrapping the second column at 80 columns while preserving the overall 2-column layout.
- Styling of focused task and descriptions: `bb` uses inverse styling on the focused task and description. `bbtl` uses bold to highlight focused tasks, with a `>` char to the left of the focused task.

<br>

## Development

### Install locally with bbin

Use this when developing from a checkout. The installed command continues to
use this local source tree, so Clojure source edits are available the next time
you run it.

1. Install [bbin](https://github.com/babashka/bbin) and make sure its bin
   directory is on your `PATH`:

   ```sh
   brew install babashka/brew/bbin
   echo 'export PATH="$PATH:$HOME/.local/bin"' >> ~/.zshrc
   exec zsh
   ```

2. Clone this repository and enter it:

   ```sh
   git clone git@github.com:paintparty/bbtl.git bbtl
   cd bbtl
   ```

3. Install this checkout under a development-safe command name:

   ```sh
   bbin install . --as bbtl-local
   ```

4. Run it:

   ```sh
   bbtl-local
   ```

5. Remove the local command when finished:

   ```sh
   bbin uninstall bbtl-local
   ```

To install the checkout as the normal command instead, use
`bbin install . --as bbtl` and run `bbtl`.

<br>

`bbtl`'s own `bb.edn` has a set of tasks to streamline the install/uninstall steps outlined above:

```sh
bb install:bbtl-local   # install this checkout as bbtl-local (dev-safe name)
bb install:bbtl         # install this checkout as bbtl (will override existing bbtl installation)
bb uninstall:bbtl-local # uninstall bbtl-local
bb uninstall:bbtl       # uninstall bbtl
```

`bbtl`'s own `bb.edn` has a additional tasks for running tests:
<br>
```sh
bb test                 # run the tests on the JVM
bb test:bb              # run the tests under Babashka
bb test:all             # run both test suites
bb ci                   # run the CI pipeline (test:all)
```

<br>

## Contributing
Issues for bugs, improvements, or features are very welcome. Please file an issue for discussion before starting or issuing a PR.

<br>

## Inspo
- [`ntl`](https://github.com/ruyadorno/ntl), (Node Task List)
- [`just --choose`](https://github.com/casey/just/blob/master/README.md#selecting-recipes-to-run-with-an-interactive-chooser), The Just command runner's `--choose` feature

<br>

## License

Copyright © 2026 Jeremiah Coyle

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0)
