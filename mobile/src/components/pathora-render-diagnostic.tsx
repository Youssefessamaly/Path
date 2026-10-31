import { Component, type ErrorInfo, type ReactNode } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts } from '@/constants/pathora-theme';

type Props = { children: ReactNode; route: string };
type State = { error: Error | null; componentStack: string };

/** Development-only diagnostic; remove this wrapper after locating the cause. */
export class PathoraRenderDiagnostic extends Component<Props, State> {
  state: State = { error: null, componentStack: '' };

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    const componentStack = info.componentStack ?? '';
    console.error('[Pathora render diagnostic] Route:', this.props.route);
    console.error('[Pathora render diagnostic] Message:', error.message);
    console.error('[Pathora render diagnostic] Component stack:\n' + componentStack);
    this.setState({ componentStack });
  }

  render() {
    const { error, componentStack } = this.state;
    if (!error) return this.props.children;
    return (
      <View style={styles.screen}>
        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.heading}>Render diagnostic</Text>
          <Text style={styles.label}>Route</Text>
          <Text style={styles.body} selectable>{this.props.route}</Text>
          <Text style={styles.label}>Error</Text>
          <Text style={styles.body} selectable>{error.message}</Text>
          <Text style={styles.label}>Component stack (copy or screenshot)</Text>
          <Text style={styles.stack} selectable>{componentStack || 'Check the Metro terminal for the component stack.'}</Text>
          <Text style={styles.note}>This is a temporary development screen, not a production fix. Restart Expo after capturing the trace.</Text>
        </ScrollView>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PathoraColors.paper },
  content: { padding: 20, gap: 12 },
  heading: { color: PathoraColors.teal700, fontSize: 22, fontFamily: PathoraFonts.en.bold },
  label: { color: PathoraColors.teal700, fontSize: 13, fontFamily: PathoraFonts.en.bold },
  body: { color: PathoraColors.ink900, fontSize: 14, fontFamily: PathoraFonts.en.regular },
  stack: { color: PathoraColors.ink900, fontSize: 11, fontFamily: PathoraFonts.en.regular },
  note: { color: PathoraColors.textSecondary, fontSize: 12, fontFamily: PathoraFonts.en.regular },
});
